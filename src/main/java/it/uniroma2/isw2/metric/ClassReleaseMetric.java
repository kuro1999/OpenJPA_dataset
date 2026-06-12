package it.uniroma2.isw2.metric;

/**
 * Rappresenta le metriche calcolate per una coppia classe-release.
 *
 * Le metriche storiche considerano solo la storia disponibile fino allo snapshot
 * della release corrente, evitando leakage temporale.
 *
 * Gli smell non sono presenti qui perché vengono calcolati separatamente.
 */
public class ClassReleaseMetric {

    private final String project;
    private final String releaseId;
    private final String classPath;

    private final int sizeLoc;
    private final int nom;
    private final double avgMethodSize;
    private final int cycloComplexity;
    private final int fanOut;

    private final int nr;
    private final double fixRate;
    private final int nAuth;

    private final int locAdded;
    private final int maxLocAdded;
    private final int churn;
    private final int maxChurn;
    private final int maxChangeSetSize;
    private final double avgModifiedDirs;

    private final long classAge;
    private final long ageSinceLastChange;
    private final double ownershipRatio;
    private final double crossDirectoryChangeRatio;

    private ClassReleaseMetric(Builder builder) {
        this.project = builder.project;
        this.releaseId = builder.releaseId;
        this.classPath = builder.classPath;
        this.sizeLoc = builder.sizeLoc;
        this.nom = builder.nom;
        this.avgMethodSize = builder.avgMethodSize;
        this.cycloComplexity = builder.cycloComplexity;
        this.fanOut = builder.fanOut;
        this.nr = builder.nr;
        this.fixRate = builder.fixRate;
        this.nAuth = builder.nAuth;
        this.locAdded = builder.locAdded;
        this.maxLocAdded = builder.maxLocAdded;
        this.churn = builder.churn;
        this.maxChurn = builder.maxChurn;
        this.maxChangeSetSize = builder.maxChangeSetSize;
        this.avgModifiedDirs = builder.avgModifiedDirs;
        this.classAge = builder.classAge;
        this.ageSinceLastChange = builder.ageSinceLastChange;
        this.ownershipRatio = builder.ownershipRatio;
        this.crossDirectoryChangeRatio = builder.crossDirectoryChangeRatio;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String project;
        private String releaseId;
        private String classPath;

        private int sizeLoc;
        private int nom;
        private double avgMethodSize;
        private int cycloComplexity;
        private int fanOut;

        private int nr;
        private double fixRate;
        private int nAuth;

        private int locAdded;
        private int maxLocAdded;
        private int churn;
        private int maxChurn;
        private int maxChangeSetSize;
        private double avgModifiedDirs;

        private long classAge;
        private long ageSinceLastChange;
        private double ownershipRatio;
        private double crossDirectoryChangeRatio;

        private Builder() {
            // Builder creato tramite ClassReleaseMetric.builder().
        }

        public Builder project(String project) {
            this.project = project;
            return this;
        }

        public Builder releaseId(String releaseId) {
            this.releaseId = releaseId;
            return this;
        }

        public Builder classPath(String classPath) {
            this.classPath = classPath;
            return this;
        }

        public Builder sizeLoc(int sizeLoc) {
            this.sizeLoc = sizeLoc;
            return this;
        }

        public Builder nom(int nom) {
            this.nom = nom;
            return this;
        }

        public Builder avgMethodSize(double avgMethodSize) {
            this.avgMethodSize = avgMethodSize;
            return this;
        }

        public Builder cycloComplexity(int cycloComplexity) {
            this.cycloComplexity = cycloComplexity;
            return this;
        }

        public Builder fanOut(int fanOut) {
            this.fanOut = fanOut;
            return this;
        }

        public Builder nr(int nr) {
            this.nr = nr;
            return this;
        }

        public Builder fixRate(double fixRate) {
            this.fixRate = fixRate;
            return this;
        }

        public Builder nAuth(int nAuth) {
            this.nAuth = nAuth;
            return this;
        }

        public Builder locAdded(int locAdded) {
            this.locAdded = locAdded;
            return this;
        }

        public Builder maxLocAdded(int maxLocAdded) {
            this.maxLocAdded = maxLocAdded;
            return this;
        }

        public Builder churn(int churn) {
            this.churn = churn;
            return this;
        }

        public Builder maxChurn(int maxChurn) {
            this.maxChurn = maxChurn;
            return this;
        }

        public Builder maxChangeSetSize(int maxChangeSetSize) {
            this.maxChangeSetSize = maxChangeSetSize;
            return this;
        }

        public Builder avgModifiedDirs(double avgModifiedDirs) {
            this.avgModifiedDirs = avgModifiedDirs;
            return this;
        }

        public Builder classAge(long classAge) {
            this.classAge = classAge;
            return this;
        }

        public Builder ageSinceLastChange(long ageSinceLastChange) {
            this.ageSinceLastChange = ageSinceLastChange;
            return this;
        }

        public Builder ownershipRatio(double ownershipRatio) {
            this.ownershipRatio = ownershipRatio;
            return this;
        }

        public Builder crossDirectoryChangeRatio(double crossDirectoryChangeRatio) {
            this.crossDirectoryChangeRatio = crossDirectoryChangeRatio;
            return this;
        }

        public ClassReleaseMetric build() {
            return new ClassReleaseMetric(this);
        }
    }

    public String getProject() {
        return project;
    }

    public String getReleaseId() {
        return releaseId;
    }

    public String getClassPath() {
        return classPath;
    }

    public int getSizeLoc() {
        return sizeLoc;
    }

    public int getNom() {
        return nom;
    }

    public double getAvgMethodSize() {
        return avgMethodSize;
    }

    public int getCycloComplexity() {
        return cycloComplexity;
    }

    public int getFanOut() {
        return fanOut;
    }

    public int getNr() {
        return nr;
    }

    public double getFixRate() {
        return fixRate;
    }

    public int getNAuth() {
        return nAuth;
    }

    public int getLocAdded() {
        return locAdded;
    }

    public int getMaxLocAdded() {
        return maxLocAdded;
    }

    public int getChurn() {
        return churn;
    }

    public int getMaxChurn() {
        return maxChurn;
    }

    public int getMaxChangeSetSize() {
        return maxChangeSetSize;
    }

    public double getAvgModifiedDirs() {
        return avgModifiedDirs;
    }

    public long getClassAge() {
        return classAge;
    }

    public long getAgeSinceLastChange() {
        return ageSinceLastChange;
    }

    public double getOwnershipRatio() {
        return ownershipRatio;
    }

    public double getCrossDirectoryChangeRatio() {
        return crossDirectoryChangeRatio;
    }
}