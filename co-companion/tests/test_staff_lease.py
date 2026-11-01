"""Offline short-lived staff entitlement checks, no live Relay requests."""
import unittest
from datetime import datetime, timezone
from unittest.mock import Mock

from tgc_cocompanion.staff_lease import RelayStaffLease, StaffLeaseRejected, legacy_staff_node_request


ID="123456789012345678"
NODE="node_test"
NOW=2000000000.0

def reply(*, grant=1, expiry=NOW+120, **changes):
    data={
        "ok": True, "verified": True, "client_type":"staff_launcher",
        "verification_method":"deployment_code", "discord_id":ID,
        "node_id":NODE, "max_instances":grant,
        "expires_at":datetime.fromtimestamp(expiry,timezone.utc).isoformat()
    }
    data.update(changes)
    return data

class LeaseTests(unittest.TestCase):
    def setUp(self):
        self.now=[NOW]
        self.answer=[reply()]
        self.requests=Mock(side_effect=lambda:self.answer[0])
        self.lease=RelayStaffLease(self.requests,enrolled_discord_id=ID,
                                  enrolled_node_id=NODE,clock=lambda:self.now[0])

    def test_no_access_until_request(self):
        self.assertIsNone(self.lease.current())
        self.requests.assert_not_called()

    def test_default_verified_one_slot(self):
        access=self.lease.refresh()
        self.assertEqual(access.valid_limit(NOW),1)
        self.assertEqual(self.lease.current(),access)

    def test_owner_four_slots_and_expiry(self):
        self.answer[0]=reply(grant=4)
        self.assertEqual(self.lease.refresh().valid_limit(NOW),4)
        self.now[0]=NOW+121
        self.assertIsNone(self.lease.current())

    def test_owner_revoke_to_one_on_refresh(self):
        self.answer[0]=reply(grant=4)
        self.lease.refresh()
        self.answer[0]=reply(grant=1)
        self.assertEqual(self.lease.refresh().max_instances,1)

    def test_failed_refresh_drops_previous_grant(self):
        self.answer[0]=reply(grant=4)
        self.lease.refresh()
        self.requests.side_effect=RuntimeError("relay unavailable")
        with self.assertRaises(StaffLeaseRejected):
            self.lease.refresh()
        self.assertIsNone(self.lease.current())

    def test_wrong_staff_or_node_denied(self):
        for changed in ({"discord_id":"123456789012345679"},{"node_id":"wrong"}):
            self.answer[0]=reply(**changed)
            with self.assertRaises(StaffLeaseRejected):
                self.lease.refresh()
            self.assertIsNone(self.lease.current())

    def test_unverified_or_wrong_type_denied(self):
        for changed in (
            {"verified":False}, {"ok":False},
            {"client_type":"mobile_companion"}, {"verification_method":"manual"},
        ):
            self.answer[0]=reply(**changed)
            with self.assertRaises(StaffLeaseRejected):
                self.lease.refresh()

    def test_bad_grants_denied(self):
        for grant in (None,True,False,0,5,-1,"4",3.5):
            self.answer[0]=reply(grant=grant)
            with self.subTest(grant=grant),self.assertRaises(StaffLeaseRejected):
                self.lease.refresh()

    def test_expired_or_excessively_long_denied(self):
        for ts in (NOW-1, NOW, NOW+181, NOW+3600):
            self.answer[0]=reply(expiry=ts)
            with self.subTest(ts=ts),self.assertRaises(StaffLeaseRejected):
                self.lease.refresh()

    def test_naive_timestamp_denied(self):
        self.answer[0]=reply(expires_at="2033-05-18T00:00:00")
        with self.assertRaises(StaffLeaseRejected):
            self.lease.refresh()

    def test_bad_payload_does_not_authorize(self):
        for value in (None, [], "hello", {}, {"ok":True}):
            self.answer[0]=value
            with self.assertRaises(StaffLeaseRejected):
                self.lease.refresh()

    def test_manual_invalidate(self):
        self.lease.refresh()
        self.lease.invalidate()
        self.assertIsNone(self.lease.current())

    def test_legacy_rpc_gated_on_verified_node_and_action(self):
        node=Mock()
        node.verified_session=True
        node.verified_data={"verified":True,"client_type":"staff_launcher"}
        node._auth_payload.return_value={"action":"cocompanion_access","node_key":"hidden"}
        node._post.return_value=reply()
        rpc=legacy_staff_node_request(node)
        result=rpc()
        self.assertEqual(result["max_instances"],1)
        node._post.assert_called_once_with(node._auth_payload.return_value,timeout=15)

    def test_unverified_legacy_rpc_never_posts(self):
        node=Mock()
        node.verified_session=False
        node.verified_data={"verified":True,"client_type":"staff_launcher"}
        rpc=legacy_staff_node_request(node)
        with self.assertRaises(StaffLeaseRejected):
            rpc()
        node._post.assert_not_called()

    def test_legacy_method_cannot_redeem_or_start(self):
        node=Mock()
        node.verified_session=True
        node.verified_data={"verified":True,"client_type":"staff_launcher"}
        node._auth_payload.return_value={"action":"cocompanion_access"}
        legacy_staff_node_request(node)()
        node.redeem_deployment.assert_not_called()
        node.verify.assert_not_called()
        node.start.assert_not_called()

if __name__=="__main__":
    unittest.main()
