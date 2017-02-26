package arash.sepasi.mockinator.sample;

import arash.sepasi.mockinator.MockOf;

@MockOf(SampleInterface.class)
public class SampleMock implements SampleInterface {

    public static final String funcReturn = "From SampleMock!";

    @Override
    public String sampleFunction() {
        return funcReturn;
    }

}
