package it.uniroma2.isw2.proportion;

import it.uniroma2.isw2.model.Release;
import it.uniroma2.isw2.model.Ticket;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Classe che arricchisce i ticket con OV e FV.
 * AV viene mantenuta dal CSV originale.
 * IV per ora viene lasciata vuota.
 *
 * Nota:
 * - OV = ultima release già disponibile quando il ticket viene aperto
 * - FV = prima release successiva al fix commit
 */
public class TicketVersionEnricher {

    private TicketVersionEnricher() {
    }

    public static List<EnhancedTicket> enrichTickets(List<Ticket> tickets, List<Release> releases) {
        List<EnhancedTicket> enhancedTickets = new ArrayList<>();

        for (Ticket ticket : tickets) {
            String openingVersion = findOpeningVersion(ticket, releases);
            String fixedVersion = findFixedVersionFromFixCommitDate(ticket, releases);

            EnhancedTicket enhancedTicket = new EnhancedTicket(
                    ticket.getTicketId(),
                    ticket.getCreationDate(),
                    ticket.getResolutionDate(),
                    ticket.getAffectedVersions(),
                    openingVersion,
                    fixedVersion,
                    "",
                    "NONE"
            );

            enhancedTickets.add(enhancedTicket);
        }

        return enhancedTickets;
    }

    private static String findOpeningVersion(Ticket ticket, List<Release> releases) {
        LocalDateTime creationDate = DateUtils.parseTicketDate(ticket.getCreationDate());

        if (creationDate == null) {
            return "";
        }

        String openingVersion = "";

        for (Release release : releases) {
            LocalDateTime releaseDate = DateUtils.parseReleaseDate(release.getDate());

            /*
             * OV = ultima release non successiva alla data di apertura del ticket.
             */
            if (!releaseDate.isAfter(creationDate)) {
                openingVersion = release.getVersionName();
            } else {
                break;
            }
        }

        return openingVersion;
    }

    private static String findFixedVersionFromFixCommitDate(Ticket ticket, List<Release> releases) {
        LocalDateTime fixCommitDate = DateUtils.parseTicketDate(ticket.getFixCommitDate());

        if (fixCommitDate == null) {
            return "";
        }

        for (Release release : releases) {
            LocalDateTime releaseDate = DateUtils.parseReleaseDate(release.getDate());

            /*
             * FV = prima release successiva al fix commit.
             */
            if (releaseDate.isAfter(fixCommitDate)) {
                return release.getVersionName();
            }
        }

        return "";
    }
}