package club.thegamerclub.nodelab;

/**
 * Exposes whether an in-app Quest Back button is actually useful.
 * Main Companion Home and Quest Tools Home are navigation roots, so the
 * floating-looking top-left Back is hidden there. Detail pages still get
 * one consistent Back control. Phone behavior remains unchanged.
 */
public interface QuestBackHost {
    boolean showQuestBack();
}
