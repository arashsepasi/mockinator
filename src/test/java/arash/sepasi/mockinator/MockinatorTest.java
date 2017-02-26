package arash.sepasi.mockinator;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;

import arash.sepasi.mockinator.sample.SampleImpl;
import arash.sepasi.mockinator.sample.SampleInterface;
import arash.sepasi.mockinator.sample.SampleMock;

import java.util.stream.Stream;

public class MockinatorTest {

    private SampleInterface s = Mockinator.mockOf(SampleInterface.class);

    @Test
    public void checkSampleImpl_ExpectCorrectValue() {
        SampleInterface i = new SampleImpl();
        Assert.assertEquals(SampleImpl.funcReturn, i.sampleFunction());
    }

    @Test
    public void checkSampleMock_ExpectCorrectValue() {
        SampleInterface m = new SampleMock();
        Assert.assertEquals(SampleMock.funcReturn, m.sampleFunction());
    }

    @Test
    public void checkMockinatorMock_ExpectSampleMockValue() {
        Assert.assertEquals(SampleMock.funcReturn, s.sampleFunction());
    }

    @Test
    public void mockMockinatorMock_WithNewValue_ExpectMockedValue() {
        final String funcReturn = "Mocked Value!";
        Mockito.when(s.sampleFunction()).thenReturn(funcReturn);
        Assert.assertEquals(funcReturn, s.sampleFunction());
    }

    @Test(expected = Exception.class)
    public void mockMockinatorMock_ThrowException_ExpectException() {
        Mockito.when(s.sampleFunction()).thenThrow(new Exception());
        s.sampleFunction();
    }

    @Test(expected = RuntimeException.class)
    public void askForInvalidMock_ExpectException() {
        Mockinator.mockOf(MockinatorTest.class);
    }

    @Test
    public void scanMoreThanOnce_ExpectNormalBehavior() {
        Mockinator.scan();
        Mockinator.scan();
        Mockinator.scan();
        Mockinator.scan();
    }

}
