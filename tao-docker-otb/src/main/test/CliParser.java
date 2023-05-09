import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.executors.ExecutorType;
import ro.cs.tao.utils.executors.OutputAccumulator;
import ro.cs.tao.utils.executors.ProcessExecutor;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.util.ArrayList;
import java.util.List;

public class CliParser {

    public static void main(String[] args) {

    }

    private static List<Path> listApplications(Path folder) throws IOException {
        return FileUtilities.listFiles(folder, "otbcli_*bat");
    }

    private static String[] getProcessOutput(Path app) {
        final ProcessExecutor executor = (ProcessExecutor) ProcessExecutor.create(ExecutorType.PROCESS, "localhost",
                new ArrayList<String>() {{ add(app.toString()); }});
        final OutputAccumulator accumulator = new OutputAccumulator();
        executor.setOutputConsumer(accumulator);
        try {
            executor.execute(false);
        } catch (IOException e) {
            e.printStackTrace();
        }
        final String output = accumulator.getOutput();
        return output != null ? output.split("\n") : new String[0];
    }
}
