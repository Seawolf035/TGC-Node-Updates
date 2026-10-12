package club.thegamerclub.nodelab;

// Authorized read-only source. Player events are delivered only to the local Companion process.
interface IPrivilegedProbe {
    String probe();
    String startWatching();
    String getWatchReport();
    String getWatchStats();
    String stopWatching();
    String pendingSightings(int limit);
    void ackSightings(String jsonIds);
    String pendingActivity(int limit);
    void ackActivity(String jsonIds);
}
