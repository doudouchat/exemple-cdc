package com.exemple.cdc.agent.commitlog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Pattern;

import org.apache.cassandra.db.commitlog.CommitLogPosition;
import org.apache.cassandra.io.util.File;

import com.exemple.cdc.agent.core.commitlog.CommitLogComponent;
import com.exemple.cdc.agent.core.commitlog.DaggerCommitLogComponent;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class CommitLogProcess {

    public static final Pattern FILENAME_REGEX_PATTERN = Pattern.compile("CommitLog-\\d+-(\\d+)(\\.log|_cdc\\.idx)", Pattern.DOTALL);

    private static final CommitLogComponent component = DaggerCommitLogComponent.create();

    private final java.io.File commitLogIndexe;

    private final java.io.File commitLog;

    private final Long segmentId;

    private int offsetOfEndOfLastWrittenCDCMutation;

    private boolean completed;

    private Integer offset;

    public CommitLogProcess(java.io.File commitLogIndexe) {
        this.commitLogIndexe = commitLogIndexe;
        this.commitLog = parseCommitLogName(commitLogIndexe);
        this.segmentId = parseSegmentId(commitLog);
    }

    @SneakyThrows
    public void process() {

        var commitLogReader = component.commitLogReader();
        var commitLogReadHandler = component.commitLogReadHandler();

        do {

            parse();

            Integer commitLogPosition;
            if (offset == null) {
                LOG.debug("Start to read the partial file : {}", commitLogIndexe.getName());
                commitLogPosition = 0;
            } else if (offset < offsetOfEndOfLastWrittenCDCMutation) {
                LOG.debug("Resume to read the partial file: {}", commitLogIndexe.getName());
                commitLogPosition = offset;
            } else {
                LOG.trace("No movement in offset in idx file: {}", commitLogIndexe.getName());
                continue;
            }

            commitLogReader.readCommitLogSegment(
                    commitLogReadHandler,
                    new File(commitLog),
                    new CommitLogPosition(segmentId,
                            commitLogPosition),
                    -1, false);

            offset = offsetOfEndOfLastWrittenCDCMutation;

        } while (!completed);

        LOG.debug("Complete idx file: {}", commitLogIndexe.getName());

        Files.delete(commitLog.toPath());
        Files.delete(commitLogIndexe.toPath());

        assert !commitLog.exists() : commitLog.getName() + " must be deleted";
        assert !commitLogIndexe.exists() : commitLogIndexe.getName() + " must be deleted";

    }

    private void parse() throws IOException {

        var lines = Files.readAllLines(commitLogIndexe.toPath(), StandardCharsets.UTF_8);

        if (!lines.isEmpty()) {
            this.offsetOfEndOfLastWrittenCDCMutation = Integer.valueOf(lines.get(0));
        }

        if (lines.size() > 1) {
            this.completed = "COMPLETED".equals(lines.get(1));
        }

    }

    private static long parseSegmentId(java.io.File commitLogIndexe) {

        var filenameMatcher = FILENAME_REGEX_PATTERN.matcher(commitLogIndexe.getName());

        assert filenameMatcher.lookingAt() : commitLogIndexe.getName() + " doesn't match " + FILENAME_REGEX_PATTERN;

        return Long.parseLong(filenameMatcher.group(1));
    }

    private static java.io.File parseCommitLogName(java.io.File commitLogIndexe) {
        var newFileName = commitLogIndexe.toPath().getFileName().toString().replace("_cdc.idx", ".log");
        return commitLogIndexe.toPath().getParent().resolve(newFileName).toFile();
    }

}
