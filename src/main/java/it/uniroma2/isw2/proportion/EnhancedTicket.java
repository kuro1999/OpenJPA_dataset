package it.uniroma2.isw2.proportion;

/**
 * Modello dati esteso per un ticket.
 * Contiene le informazioni necessarie per la fase di proportion.
 */
public class EnhancedTicket {
    private final String ticketId;
    private final String creationDate;
    private final String resolutionDate;
    private final String affectedVersions;
    private final String openingVersion;
    private final String fixedVersion;
    private final String injectedVersion;
    private final String injectedVersionSource;

    private EnhancedTicket(Builder builder) {
        this.ticketId = builder.ticketId;
        this.creationDate = builder.creationDate;
        this.resolutionDate = builder.resolutionDate;
        this.affectedVersions = builder.affectedVersions;
        this.openingVersion = builder.openingVersion;
        this.fixedVersion = builder.fixedVersion;
        this.injectedVersion = builder.injectedVersion;
        this.injectedVersionSource = builder.injectedVersionSource;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String ticketId;
        private String creationDate;
        private String resolutionDate;
        private String affectedVersions;
        private String openingVersion;
        private String fixedVersion;
        private String injectedVersion;
        private String injectedVersionSource;

        private Builder() {
            // Builder creato tramite EnhancedTicket.builder().
        }

        public Builder ticketId(String ticketId) {
            this.ticketId = ticketId;
            return this;
        }

        public Builder creationDate(String creationDate) {
            this.creationDate = creationDate;
            return this;
        }

        public Builder resolutionDate(String resolutionDate) {
            this.resolutionDate = resolutionDate;
            return this;
        }

        public Builder affectedVersions(String affectedVersions) {
            this.affectedVersions = affectedVersions;
            return this;
        }

        public Builder openingVersion(String openingVersion) {
            this.openingVersion = openingVersion;
            return this;
        }

        public Builder fixedVersion(String fixedVersion) {
            this.fixedVersion = fixedVersion;
            return this;
        }

        public Builder injectedVersion(String injectedVersion) {
            this.injectedVersion = injectedVersion;
            return this;
        }

        public Builder injectedVersionSource(String injectedVersionSource) {
            this.injectedVersionSource = injectedVersionSource;
            return this;
        }

        public EnhancedTicket build() {
            return new EnhancedTicket(this);
        }
    }

    public String getTicketId() {
        return ticketId;
    }

    public String getCreationDate() {
        return creationDate;
    }

    public String getResolutionDate() {
        return resolutionDate;
    }

    public String getAffectedVersions() {
        return affectedVersions;
    }

    public String getOpeningVersion() {
        return openingVersion;
    }

    public String getFixedVersion() {
        return fixedVersion;
    }

    public String getInjectedVersion() {
        return injectedVersion;
    }

    public String getInjectedVersionSource() {
        return injectedVersionSource;
    }
}