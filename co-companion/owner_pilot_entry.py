"""PyInstaller / source owner-preview entrypoint. Never deploys TGC services."""
from tgc_cocompanion.owner_pilot import main

if __name__ == "__main__":
    main()
