"""Synthetic protocol tests, not evidence of provider coordinate accuracy."""
import json
import math
from unittest.mock import patch
from common import stamp
from policy_helpers import PolicyCase


def metadata(lat=30.6645, lng=111.3694):
    return {'provider':'AMAP','rawCoordinateSystem':'GCJ02','rawLatitude':lat+.002,
        'rawLongitude':lng+.005,'normalizedLatitude':lat,'normalizedLongitude':lng,
        'method':'AMAP_SDK_FORWARD_ITERATIVE_V1','sdkVersion':'11.2.100','residualMeters':.05}


class AmapBoundary(PolicyCase):
    def test_role_config_only_exposes_its_android_identifier(self):
        with patch.dict('os.environ', {'AMAP_DRIVER_ANDROID_KEY':'a'*32,'AMAP_PASSENGER_ANDROID_KEY':'b'*32,
                'AMAP_WEB_JS_SECURITY_CODE':'test-server-secret'}):
            p,d=self.setup_pair()
            self.assertEqual(d['mapConfig']['androidKey'],'a'*32)
            self.assertEqual(p['mapConfig']['androidKey'],'b'*32)
            self.assertNotIn('test-server-secret',json.dumps(d)+json.dumps(p))
            self.assertNotIn('mapConfig',json.dumps(d['state']))

    def test_metadata_is_bound_to_selected_coordinates_and_kept_in_history(self):
        p,d=self.setup_pair();m=metadata()
        o=self.create(p['token'],destinationCoordinateMetadata=m)
        self.assertEqual(o['destinationCoordinateMetadata'],m)
        history=self.request('/api/passenger/state',token=p['token'])['historicalLocations']
        self.assertEqual(next(x for x in history if x['address']==o['destination'])['coordinateMetadata'],m)
        self.pa(p['token'],'order.update',{'id':o['id'],'destination':'新的目的地',
            'destinationLat':30.665,'destinationLng':111.37,'destinationCoordinateMetadata':m},expected=400)
        self.assertEqual(self.get_order(p['token'],o['id'])['destinationCoordinateMetadata'],m)

    def test_manual_reselection_clears_old_metadata_and_no_metadata_does_not_change_old_context(self):
        p,d=self.setup_pair();o=self.create(p['token'],destinationCoordinateMetadata=metadata())
        self.pa(p['token'],'order.update',{'id':o['id'],'destination':'手动新地点','destinationLat':30.665,'destinationLng':111.37})
        self.assertNotIn('destinationCoordinateMetadata',self.get_order(p['token'],o['id']))
        def check(svc):
            value=svc.shared_order(o['id']);value['stage']='TRIP'
            self.assertNotIn('destinationCoordinateMetadata',svc.distance_context(value))
        self.mutate_fixture(check)

    def test_normalization_near_threshold_requires_consent_and_never_changes_device_gps(self):
        p,d=self.setup_pair();o=self.create(p['token'],destinationCoordinateMetadata=metadata())
        driver=d['profile']['driver']['id']
        def check(svc):
            value=svc.shared_order(o['id']);value.update(stage='TRIP',driverId=driver)
            for distance,within in [(297,True),(299,False),(300,False),(301,False)]:
                raw=svc.trip_location({'latitude':o['destinationLat']+math.degrees(distance/6371000),
                    'longitude':o['destinationLng'],'accuracy':5,'time':stamp(),'coordinateSystem':'WGS84'})
                svc.db.execute('UPDATE accounts SET location=? WHERE id=?',(json.dumps(raw),driver))
                evidence=svc.shared_distance_evidence(value)
                self.assertEqual(evidence['withinDistance'],within)
                self.assertEqual(evidence['driverLocation']['source'],'DEVICE_GPS')
                self.assertEqual(evidence['normalizationBudgetMeters'],2)
            value.pop('destinationCoordinateMetadata')
            raw=svc.trip_location({'latitude':o['destinationLat']+math.degrees(299/6371000),
                'longitude':o['destinationLng'],'accuracy':5,'time':stamp(),'coordinateSystem':'WGS84'})
            svc.db.execute('UPDATE accounts SET location=? WHERE id=?',(json.dumps(raw),driver))
            self.assertTrue(svc.shared_distance_evidence(value)['withinDistance'])
            self.assertEqual(svc.shared_distance_evidence(value)['normalizationBudgetMeters'],0)
        self.mutate_fixture(check)

    def test_metadata_cannot_be_sent_as_device_location(self):
        p,d=self.setup_pair();o=self.create(p['token'])
        self.da(d['token'],'location.report',{'latitude':30.66,'longitude':111.36,'accuracy':5,
            'time':stamp(),'rawCoordinateSystem':'GCJ02','method':'AMAP_SDK_FORWARD_ITERATIVE_V1'},expected=400)
        self.pa(p['token'],'location.report',{'id':o['id'],'latitude':30.66,'longitude':111.36,
            'accuracy':5,'time':stamp(),'coordinateSystem':'GCJ02'},expected=400)
