"""Offline Co-Companion lease -> four-slot launcher integration: no live Relay/VRChat."""
from datetime import datetime,timezone
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from tgc_cocompanion.staff_lease import RelayStaffLease,StaffLeaseRejected
from tgc_cocompanion.launch_controller import ManagedVRChatLauncher
from tgc_cocompanion.permissions import AccessDenied


ID="123456789012345678"
NODE="node-lease-test"
NOW=2000000000.0

class Server:
    def __init__(self):
        self.allow=1
        self.up=True
        self.revoked=False
        self.now=NOW

    def response(self):
        if not self.up:
            raise OSError("Offline")
        return {
            "ok":True,"verified":not self.revoked,
            "discord_id":ID,"node_id":NODE,
            "client_type":"staff_launcher","verification_method":"deployment_code",
            "max_instances":self.allow,
            "expires_at":datetime.fromtimestamp(self.now+120,timezone.utc).isoformat(),
        }

class IntegratedGrants(unittest.TestCase):
    def setUp(self):
        self.tmp=TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.exe=Path(self.tmp.name)/"launch.exe"
        self.exe.touch()
        self.remote=Server()
        self.status={}
        self.spawns=[]
        self.lease=RelayStaffLease(
            self.remote.response,
            enrolled_discord_id=ID,
            enrolled_node_id=NODE,
            clock=lambda:self.remote.now,
        )
        self.launcher=ManagedVRChatLauncher(
            self.lease.current,
            lambda:True,
            lambda argv:self.spawns.append(argv),
            lambda slot, marker:self.status.get(slot),
            executable=self.exe,
            vr_slot=1,
            clock=lambda:self.remote.now,
        )

    def test_never_grant_before_contacting_server(self):
        with self.assertRaises(AccessDenied):
            self.launcher.launch(1)
        self.assertEqual(self.spawns,[])

    def test_default_one_then_owner_four_and_revoke(self):
        self.lease.refresh()
        self.launcher.launch(1)
        self.status[1]=1001
        self.launcher.refresh()
        with self.assertRaises(AccessDenied):
            self.launcher.launch(2)
        self.remote.allow=4
        self.lease.refresh()
        for slot in (2,3,4):
            self.launcher.launch(slot)
            self.status[slot]=1000+slot
            self.launcher.refresh()
        self.assertEqual(len(self.spawns),4)
        self.remote.allow=1
        self.lease.refresh()
        # Never terminate active sessions because of a grant change.
        self.assertEqual(len([s for s in self.launcher.refresh() if s.pid]),4)
        with self.assertRaises(AccessDenied):
            self.launcher.launch(4)

    def test_relay_disconnect_revokes_cached_extra_slots(self):
        self.remote.allow=4
        self.lease.refresh()
        self.launcher.launch(1)
        self.status[1]=2001
        self.launcher.refresh()
        self.remote.up=False
        with self.assertRaises(StaffLeaseRejected):
            self.lease.refresh()
        with self.assertRaises(AccessDenied):
            self.launcher.launch(2)
        self.assertEqual(self.launcher.refresh()[0].pid,2001)

    def test_expired_grant_rejected_without_background_refresh(self):
        self.remote.allow=4
        self.lease.refresh()
        self.remote.now+=121
        with self.assertRaises(AccessDenied):
            self.launcher.launch(1)

    def test_roster_revocation_invalidates_entire_access(self):
        self.lease.refresh()
        self.remote.revoked=True
        with self.assertRaises(StaffLeaseRejected):
            self.lease.refresh()
        with self.assertRaises(AccessDenied):
            self.launcher.launch(1)
        self.assertEqual(self.spawns,[])


if __name__=="__main__":
    unittest.main()
