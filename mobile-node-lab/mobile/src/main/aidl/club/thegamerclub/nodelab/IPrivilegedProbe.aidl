package club.thegamerclub.nodelab;

// Temporary privileged, read-only diagnostic. Returns summary counts, never raw logs.
interface IPrivilegedProbe {
    String probe();
}
