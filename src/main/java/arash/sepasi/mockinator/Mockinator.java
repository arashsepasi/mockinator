package arash.sepasi.mockinator;


import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class provides mock implementation of classes for use in unit testing. <br>
 * It scans (heuristically scoped) packages across all available projects, looking for classes with the @
 * {@link MockOf} (Class) annotation. Such classes are kept track of, and a Mockito spy of them is made available via a
 * call to {@link #mockOf(Class)}. In this manner all generic mock behavior may be defined in one place (the class with
 * the {@literal @MockOf} annotation), but the mock received from {@link #mockOf(Class)} may be further customized via
 * standard Mockito functionality (e.g. {@link Mockito#when(Object)}).
 *
 * @author Arash Sepasi
 *
 */
public class Mockinator {

    private static final Logger log = LoggerFactory.getLogger(Mockinator.class);
    // Map of real class to its mock
    private static final Map<Class<?>, Class<?>> mocks = new ConcurrentHashMap<>();
    // Scanner to scan for mocks
    private static final MockScanner scanner = new MockScanner();

    /**
     * Mockinator should be used as a utility class, no need for a public constructor
     */
    protected Mockinator() {}

    /**
     * Scans the specified package(s) and their subpackages across all available projects, looking for classes with the @
     * {@link MockOf}(Class) annotation. The {@link #mocks} map is updated with the real class or interface as keys, and
     * the mock class as values. <br>
     * Automatically called with calls to {@link #mockOf(Class)}, if needed.
     *
     * @param packagesToScan The package(s) to scan. If none are provided, a default top-level package dynamically calculated
     *                       from the calling class's package is scanned instead.
     */
    public static void scan(String... packagesToScan) {
        doScan(packagesToScan);
    }

    /**
     * Internal method to do actual scanning. This is used so that whether calls come in from public scan, or from mockOf, the depth of the call stack
     * will be the same and so we can look for the calling class's package uniformly.
     * @param packagesToScan
     */
    private static void doScan(String... packagesToScan) {
        scanner.scan(packagesToScan);
    }

    /**
     * This method will instantiate and return an appropriate mock for the specified class. This mock is in reality a
     * Mockito spy (via {@link Mockito#spy(Class)}) of the class found during the scan with a @{@link MockOf}(clazz)
     * annotation whose {@literal clazz} is the same as the class specified here.
     *
     * @param realClass
     *            The interface or real class to grab the mock for
     * @return A Mockito.spy of the mock of the realClass. A new unique value each time!
     * @throws RuntimeException if Mockinator could not find a mock of the requested class
     *
     * @implNote If a mock is not already found for the requested class, a scan is performed on the package of that class.
     * If a mock is not found in that package, a scan is performed on a default top-level package dynamically calculated
     * from the calling class's package. If a mock is still not found, then an exception is thrown.
     */
    @SuppressWarnings("unchecked")
    public static <T> T mockOf(Class<T> realClass) {
        log.debug("Looking for mockOf {}", realClass);
        if (!mocks.containsKey(realClass)) {
            final String classPackage = realClass.getPackage().getName();
            log.trace("Did not have mockOf {} in {}, scanning classPackage {}...", realClass, mocks, classPackage);
            doScan(classPackage);
        }
        if (!mocks.containsKey(realClass)) {
            log.trace("Did not have mockOf {} in {}, scanning default location...", realClass, mocks);
            doScan();
        }
        if (!mocks.containsKey(realClass)) {
            throw new RuntimeException(String.format("Could not find mock of class %s", realClass));
        }
        return (T) Mockito.spy(mocks.get(realClass));
    }

    /**
     * Inner class to do the actual scanning for mocks
     */
    private static class MockScanner {
        // Scanner to search for mocks
        private final ClassPathScanningCandidateComponentProvider scanner;
        // Keeps track of packages we've already scanned, so we don't rescan for no reason
        private final Set<String> scannedPackages;
        // Can resolve which class made the call to Mockinator, so that we can scan that class's package by default
        private final CallingClassResolver callingClassResolver;

        /**
         * Only used by Mockinator. Hidden constructor initializes variables
         */
        protected MockScanner() {
            scanner = new ClassPathScanningCandidateComponentProvider(false);
            // Only consider classes which have the @MockOf annotation to be mocks
            scanner.addIncludeFilter(new AnnotationTypeFilter(MockOf.class));
            scannedPackages = ConcurrentHashMap.newKeySet();
            callingClassResolver = new CallingClassResolver();
        }

        /**
         * Scans the desired package and adds any potential mock candidates to the provided set
         * Will not do any scanning if the provided package is null or ""
         * Will also not do any scanning if we've already scanned the desired package, or any super-package of it
         * @param packageToScan
         * @param candidates
         */
        private void addCandidates(String packageToScan, Set<BeanDefinition> candidates) {
            if(StringUtils.isEmpty(packageToScan)) {
                log.trace("Specified packageToScan {} is invalid!", packageToScan);
                return;
            }
            if(scannedPackages.contains(packageToScan) || scannedPackages.stream().anyMatch(packageToScan::startsWith)) {
                log.trace("Already scanned {}!", packageToScan);
                return;
            }
            /**
             * We will synchronize on the scanner, since after our scan we will add the scanned package to the
             * scanner's excludeFilters, and we need this to be done atomically
             */
            synchronized (scanner) {
                candidates.addAll(scanner.findCandidateComponents(packageToScan));
                // Since we scanned this package, make sure to ignore all potential candidates which are in packageToScan's subpackages
                final Pattern thisPackage = Pattern.compile(String.format("^%s\\..*", packageToScan.replaceAll("\\.", "\\\\\\.")));
                log.trace("Adding exclude filter with {}", thisPackage);
                scanner.addExcludeFilter(new RegexPatternTypeFilter(thisPackage));
            }
            // Keep track of the package we just scanned, so we don't try to rescan it
            scannedPackages.add(packageToScan);
        }

        /**
         * Scans the specified package(s) looking for mocks, updating the Mockinator's mocks map accordingly
         * If no packages are specified, will default to the calling class's super-package (first 2 tokens)
         * @param packagesToScan
         */
        protected void scan(String... packagesToScan) {
            log.trace("Scan getting called on {}!", packagesToScan);

            if(packagesToScan == null || packagesToScan.length == 0) {
                packagesToScan = new String[]{callingClassResolver.getCallingClassSuperPackage()};
                log.trace("Since no packagesToScan were provided, performing default scan on {}!", packagesToScan);
            }

            final Set<BeanDefinition> candidates = new HashSet<>();

            Stream.of(packagesToScan).forEach(p -> addCandidates(p, candidates));

            candidates.stream().forEach(c -> {
                try {
                    Class<?> mockClass = Class.forName(c.getBeanClassName());
                    log.debug("Found mock class: {}", mockClass);
                    MockOf mockOf = mockClass.getAnnotation(MockOf.class);
                    Class<?> realClass = mockOf.value();
                    log.debug("It is a mock for: {}", realClass);
                    mocks.put(realClass, mockClass);
                } catch (Exception e) {
                    log.warn("Couldn't grab class for {}", c.getBeanClassName(), e);
                }
            });
        }

        /**
         * Inner class which can resolve the class which asked Mockinator to scan
         */
        private static class CallingClassResolver extends SecurityManager {
            public String getCallingClassSuperPackage() {
                // The way Mockinator is organized, the calling class will always be at position 4 by the time we're here
                log.trace("getCallingClassSuperPackage from {}", getClassContext()[4]);
                // Get the calling class's package, and only return the first 2 tokens of its package
                return Stream.of(getClassContext()[4].getPackage().getName().split("\\."))
                        .limit(2)
                        .collect(Collectors.joining("."));
            }
        }
    }
}

