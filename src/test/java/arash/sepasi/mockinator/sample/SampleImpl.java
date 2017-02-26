package arash.sepasi.mockinator.sample;

public class SampleImpl implements SampleInterface {

    public static final String funcReturn = "From SampleImpl!";

    @Override
    public String sampleFunction() {
        return funcReturn;
    }

}
