package arash.sepasi.mockinator;

import org.junit.Assert;
import org.junit.Test;

import arash.sepasi.mockinator.sample.SampleInterface;
import arash.sepasi.mockinator.sample.SampleMock;

public class MockOfTest {

    @Test
    public void checkSampleMockIsAnnotatedCorrectly() {
        MockOf a = SampleMock.class.getAnnotation(MockOf.class);
        Assert.assertNotNull(a);

        Class<?> c = a.value();
        Assert.assertNotNull(c);
        Assert.assertEquals(SampleInterface.class, c);
    }

}
