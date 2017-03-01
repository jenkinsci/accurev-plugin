package hudson.plugins.accurev;

import static java.nio.charset.StandardCharsets.UTF_8;
import hudson.model.Job;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class WorkspaceTransaction {
    private static final String WORKSPACELASTTRANSFILENAME = "WorkspaceLastTrans.txt";

    public static void setWorkspaceLastTransaction(Job<?, ?> job, String previous) throws IOException {
        if (job == null)
            throw new IOException("Job is null");
        File f = new File(job.getRootDir(), WORKSPACELASTTRANSFILENAME);
        try (BufferedWriter br = Files.newBufferedWriter(f.toPath(), UTF_8)) {
            br.write(previous);
        }
    }

    public static String getWorkspaceLastTransaction(Job<?, ?> job) throws IOException {
        if (job == null)
            throw new IOException("Job is null");
        File f = new File(job.getRootDir(), WORKSPACELASTTRANSFILENAME);
        if (!f.exists()) {
            if (f.createNewFile())
                return "";
            else
                throw new IOException("Failed to create file");
        }
        try (BufferedReader br = Files.newBufferedReader(f.toPath(), UTF_8)) {
            return br.readLine();
        }
    }

}
