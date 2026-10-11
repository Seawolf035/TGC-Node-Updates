package club.thegamerclub.nodelab;

// Authorized read-only diagnostic service. No raw log text or user IDs leave the service.
interface IPrivilegedProbe {
    String probe();
    String startWatching();
    String getWatchReport();
    String getWatchStats();
    String stopWatching();
    String pendingSightings(int limit);
    void ackSightings(String jsonIds);
}
