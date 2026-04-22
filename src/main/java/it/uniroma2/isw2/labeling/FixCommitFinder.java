package it.uniroma2.isw2.labeling;

import it.uniroma2.isw2.proportion.EnhancedTicket;
import it.uniroma2.isw2.model.TicketFixCommit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FixCommitFinder {

    private FixCommitFinder() {
    }

    public static List<TicketFixCommit> findFixCommits(List<EnhancedTicket> tickets,
                                                       String repositoryPath) throws IOException {
        List<TicketFixCommit> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (EnhancedTicket ticket : tickets) {
            List<String> lines = findFixCommitsForTicket(ticket.getTicketId(), repositoryPath);

            for (String line : lines) {
                String[] parts = line.split("\t");
                if (parts.length < 2) {
                    continue;
                }

                String hash = parts[0].trim();
                long epochSeconds = Long.parseLong(parts[1].trim());

                String key = ticket.getTicketId() + "|" + hash;
                if (seen.add(key)) {
                    result.add(new TicketFixCommit(ticket.getTicketId(), hash, epochSeconds));
                }
            }
        }

        return result;
    }

    private static List<String> findFixCommitsForTicket(String ticketId,
                                                        String repositoryPath) throws IOException {
        try {
            return GitCommandRunner.runCommand(
                    repositoryPath,
                    "git",
                    "log",
                    "--all",
                    "--regexp-ignore-case",
                    "--grep=" + ticketId,
                    "--format=%H\t%ct"
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interruzione durante la ricerca dei fix commit.", e);
        }
    }
}