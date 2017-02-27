# Mockinator
Mockinator is a Mockito and Spring-based lightweight Java framework to centralize mocks for unit testing.
* [Motivation](#motivation)
* [A better way](#a-better-way)

### Motivation
In order to correctly unit test a function, ideally the only logic being tested should belong to the function under test. Therefore if the function under test has dependencies on other logical units (classes, functions, etc.), these dependencies should be mocked to isolate the function under test. However, often, a single logical unit is used in more than just one place, in more than just one function. Therefore unit tests of these multiple functions requires repeated mocking of that same logical unit, often in exactly the same way. As an example, take the following database utility class, which provides data access abstraction to a database containing users:
```java
public class UserDatabase {
    // Connection to the database. Details are kept out for brevity.
    private DatabaseConnection db = new DatabaseConnection(...);
    
    // Returns a User by id, or null if no such User, or a database connection error.
    public User getUserById(UUID id) {
        try {
            SearchContext search = db.prepareSearch("users");
            search.setQuery(String.format("id == %s", id));
            search.setCount(1);
            User user = search.execute();
            return user;
        } catch (NoSuchTableException|BadQueryException|NoSuchEntityException e) {
            log.error("Failed to getUserById({}): ", id, e);
            return null;
        }
    }
    
    // Persists a User into the database. Will not act if user is null.
    public void persistUser(User user) {
        if (user == null) {
            log.debug("Will not persist a null user!");
            return;
        }
        try {
            PersistContext persist = db.preparePersist("users");
            persist.setContent(user);
            persist.execute();
        } catch (NoSuchTableException|IncompatibleDataException e) {
            log.error("Failed to persistUser({}): ", user, e);
    }
}
```
Now consider two other classes, which use the above `UserDatabase` class to perform some business logic:
```java
public class UserPaymentService {
    private UserDatabase userDb;
    
    public UserPaymentService(UserDatabase userDb) {
        this.userDb = userDb;
    }
    
    // Pays the user with the specified id the specified amount
    public void payUser(UUID userId, Integer amount) {
        User user = userDb.getById(userId);
        if (user == null) {
            log.error("Failed to pay user {}!", userId);
            return;
        }
        user.setSavings(user.getSavings() + amount);
        userDb.persistUser(user);
    }
}
```
```java
public class UserZipcodeService {
    private UserDatabase userDb;
    
    public UserZipcodeService(UserDatabase userDb) {
        this.userDb = userDb;
    }
    
    // Relocates the user with the specified id to the specified zipcode
    public void relocateUser(UUID userId, String zipcode) {
        User user = userDb.getById(userId);
        if (user == null) {
            log.error("Failed to relocate user {}!", userId);
            return;
        }
        user.setZipcode(zipcode);
        userDb.persistUser(user);
    }    
}
```
Now to perform unit testing on the `UserPaymentService.payUser` function, you'll likely do something similar to:
```java
public class UserPaymentServiceTest {
    UserDatabase userDbMock = Mockito.mock(UserDatabase.class);
    UserPaymentService service = new UserPaymentService(userDbMock);
    User testUser;
    
    @Before
    public void setupUserDb() {
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setSavings(0);
        Mockito
            .when(userDbMock.getById(Mockito.any(UUID.class)))
            .thenReturn(testUser);
        Mockito
            .when(userDbMock.persistUser(Mockito.any(User.class)))
            .then(i -> {
                User user = i.getArgument(0);
                testUser.setSavings(user.getSavings());
            });
    }
    
    @Test
    public void payUser_SomePositiveAmount_ExpectAmountMoreInUsersSavings() {
        Integer savings = testUser.getSavings();
        service.payUser(testUser.getId(), 100);
        Assert.assertEquals(savings+100, testUser.getSavings());
    }
}
```
And to perform unit testing on the `UserZipcodeService.relocateUser` function, you'll likely do something like this:
```java
public class UserZipcodeServiceTest {
    UserDatabase userDbMock = Mockito.mock(UserDatabase.class);
    UserZipcodeService service = new UserZipcodeService(userDbMock);
    String zipcode_A = "11111";
    String zipcode_B = "22222";
    User testUser;
    
    @Before
    public void setupUserDb() {
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        Mockito
            .when(userDbMock.getById(Mockito.any(UUID.class)))
            .thenReturn(testUser);
        Mockito
            .when(userDbMock.persistUser(Mockito.any(User.class)))
            .then(i -> {
                User user = i.getArgument(0);
                testUser.setZipcode(user.getZipcode());
            });
    }
    
    @Test
    public void relocateUser_fromZipcodeA_toZipcodeB_ExpectInZipcodeB() {
        testUser.setZipcode(zipcode_A);
        service.relocateUser(testUser.getId(), zipcode_B);
        Assert.assertEquals(zipcode_B, testUser.getZipcode());
    }
}
```
As you can see, some mocking behavior is repeated in the two tests. While this is a silly example, in the real world a similar pattern is observed, often to a much greater extent (specially where individual tests mock their desired behavior).

### A Better Way
With Mockinator, you can write a class specifically designed to contain all the usual mocked behavior of the UserDatabase class, as such:
```java
// The @MockOf annotation lets Mockinator know that this class is a mock implementation
// of the UserDatabase class (or interface)
@MockOf(UserDatabase.class)
public class UserDatabaseMock {
    // For the mock, we can just use a HashMap instead of connecting to the real database:
    private Map<UUID, User> db = new HashMap<>();
    
    // Returns a User by id, or null if no such User.
    public User getUserById(UUID id) {
        return db.get(id);
    }
    
    // Persists a User into the database. Will not act if user is null.
    public void persistUser(User user) {
        if (user == null)
            return;
        db.put(user.getId(), user);
    }
}
```
And now the same tests above may be written in a cleaner fashion:
```java
public class UserPaymentServiceTest {
    // Ask Mockinator for your mock of the UserDatabase class:
    UserDatabase userDbMock = Mockinator.mockOf(UserDatabase.class);
    UserPaymentService service = new UserPaymentService(userDbMock);
    User testUser;
    
    @Before
    public void setupUserDb() {
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setSavings(0);
        
        userDbMock.persistUser(testUser);
        // No need for mocking behavior!
        // Of course, userDbMock is still a Mockito Spy of UserDatabaseMock, so you
        // *could* mock it for individual tests if you need to.
    }
    
    @Test
    public void payUser_SomePositiveAmount_ExpectAmountMoreInUsersSavings() {
        Integer savings = testUser.getSavings();
        service.payUser(testUser.getId(), 100);
        User user = userDbMock.getById(testUser.getId());
        Assert.assertEquals(savings+100, user.getSavings());
    }
}
```
```java
public class UserZipcodeServiceTest {
    UserDatabase userDbMock = Mockinator.mock(UserDatabase.class);
    UserZipcodeService service = new UserZipcodeService(userDbMock);
    String zipcode_A = "11111";
    String zipcode_B = "22222";
    User testUser;
    
    @Before
    public void setupUserDb() {
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        
        userDbMock.persistUser(testUser);
    }
    
    @Test
    public void relocateUser_fromZipcodeA_toZipcodeB_ExpectInZipcodeB() {
        testUser.setZipcode(zipcode_A);
        service.relocateUser(testUser.getId(), zipcode_B);
        User user = userDbMock.getById(testUser.getId());
        Assert.assertEquals(zipcode_B, user.getZipcode());
    }
}
```
