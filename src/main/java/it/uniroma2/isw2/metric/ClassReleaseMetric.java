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

    public ClassReleaseMetric(String project,
                              String releaseId,
                              String classPath,
                              int sizeLoc,
                              int nom,
                              double avgMethodSize,
                              int cycloComplexity,
                              int fanOut,
                              int nr,
                              double fixRate,
                              int nAuth,
                              int locAdded,
                              int maxLocAdded,
                              int churn,
                              int maxChurn,
                              int maxChangeSetSize,
                              double avgModifiedDirs,
                              long classAge,
                              long ageSinceLastChange,
                              double ownershipRatio,
                              double crossDirectoryChangeRatio) {
        this.project = project;
        this.releaseId = releaseId;
        this.classPath = classPath;
        this.sizeLoc = sizeLoc;
        this.nom = nom;
        this.avgMethodSize = avgMethodSize;
        this.cycloComplexity = cycloComplexity;
        this.fanOut = fanOut;
        this.nr = nr;
        this.fixRate = fixRate;
        this.nAuth = nAuth;
        this.locAdded = locAdded;
        this.maxLocAdded = maxLocAdded;
        this.churn = churn;
        this.maxChurn = maxChurn;
        this.maxChangeSetSize = maxChangeSetSize;
        this.avgModifiedDirs = avgModifiedDirs;
        this.classAge = classAge;
        this.ageSinceLastChange = ageSinceLastChange;
        this.ownershipRatio = ownershipRatio;
        this.crossDirectoryChangeRatio = crossDirectoryChangeRatio;
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