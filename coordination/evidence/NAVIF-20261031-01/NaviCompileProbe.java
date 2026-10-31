package probe;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.Button;
import com.amap.api.navi.*;
import com.amap.api.navi.model.*;
import com.amap.api.navi.enums.NaviType;
import com.amap.api.maps.MapsInitializer;
import com.amap.api.services.core.ServiceSettings;
import java.util.List;
// COMPILE ONLY. Never instantiated or run. No keys, GPS, provider calls or business writes executed.
public final class NaviCompileProbe implements AMapNaviListener {
 AMapNavi navi; AMapNaviView view;
 public FrameLayout create(Context context, Bundle saved, boolean displayed, boolean consented) throws Exception {
  NaviSetting.updatePrivacyShow(context,displayed,displayed);
  NaviSetting.updatePrivacyAgree(context,consented);
  MapsInitializer.updatePrivacyShow(context,displayed,displayed);
  MapsInitializer.updatePrivacyAgree(context,consented);
  ServiceSettings.updatePrivacyShow(context,displayed,displayed);
  ServiceSettings.updatePrivacyAgree(context,consented);
  navi=AMapNavi.getInstance(context); navi.addAMapNaviListener(this);
  view=new AMapNaviView(context); view.onCreate(saved);
  AMapNaviViewOptions options=view.getViewOptions();
  options.setSettingMenuEnabled(false); options.setRouteListButtonShow(false);
  options.setPointToCenter(.5,.55); view.setViewOptions(options);
  FrameLayout root=new FrameLayout(context);
  FrameLayout.LayoutParams mapParams=new FrameLayout.LayoutParams(-1,-1);
  mapParams.bottomMargin=(int)(96*context.getResources().getDisplayMetrics().density);
  root.addView(view,mapParams);
  Button orderAction=new Button(context);orderAction.setText("业务按钮仅占位");
  root.addView(orderAction,new FrameLayout.LayoutParams(-1,mapParams.bottomMargin,Gravity.BOTTOM));
  return root;
 }
 public boolean plan(List<NaviLatLng> from,List<NaviLatLng> to,List<NaviLatLng> stops) {
  return navi.calculateDriveRoute(from,to,stops,10);
 }
 public boolean startAfterSuccessfulRoute(int routeId) {
  navi.selectRouteId(routeId);navi.setUseInnerVoice(true,true);return navi.startNavi(NaviType.GPS);
 }
 public void resume(){view.onResume();}
 public void pause(){view.onPause();}
 public void save(Bundle saved){view.onSaveInstanceState(saved);}
 public void close(){navi.removeAMapNaviListener(this);navi.stopNavi();view.onDestroy();AMapNavi.destroy();}
 @Override public void onInitNaviFailure() {}
 @Override public void onInitNaviSuccess() {}
 @Override public void onStartNavi(int a0) {}
 @Override public void onTrafficStatusUpdate() {}
 @Override public void onLocationChange(com.amap.api.navi.model.AMapNaviLocation a0) {}
 @Override public void onGetNavigationText(int a0, java.lang.String a1) {}
 @Override public void onGetNavigationText(java.lang.String a0) {}
 @Override public void onEndEmulatorNavi() {}
 @Override public void onArriveDestination() {}
 @Override public void onCalculateRouteFailure(int a0) {}
 @Override public void onReCalculateRouteForYaw() {}
 @Override public void onReCalculateRouteForTrafficJam() {}
 @Override public void onArrivedWayPoint(int a0) {}
 @Override public void onGpsOpenStatus(boolean a0) {}
 @Override public void onNaviInfoUpdate(com.amap.api.navi.model.NaviInfo a0) {}
 @Override public void updateCameraInfo(com.amap.api.navi.model.AMapNaviCameraInfo[] a0) {}
 @Override public void updateIntervalCameraInfo(com.amap.api.navi.model.AMapNaviCameraInfo a0, com.amap.api.navi.model.AMapNaviCameraInfo a1, int a2) {}
 @Override public void onServiceAreaUpdate(com.amap.api.navi.model.AMapServiceAreaInfo[] a0) {}
 @Override public void showCross(com.amap.api.navi.model.AMapNaviCross a0) {}
 @Override public void hideCross() {}
 @Override public void showModeCross(com.amap.api.navi.model.AMapModelCross a0) {}
 @Override public void hideModeCross() {}
 @Override public void showLaneInfo(com.amap.api.navi.model.AMapLaneInfo[] a0, byte[] a1, byte[] a2) {}
 @Override public void showLaneInfo(com.amap.api.navi.model.AMapLaneInfo a0) {}
 @Override public void hideLaneInfo() {}
 @Override public void onCalculateRouteSuccess(int[] a0) {}
 @Override public void notifyParallelRoad(int a0) {}
 @Override public void OnUpdateTrafficFacility(com.amap.api.navi.model.AMapNaviTrafficFacilityInfo[] a0) {}
 @Override public void OnUpdateTrafficFacility(com.amap.api.navi.model.AMapNaviTrafficFacilityInfo a0) {}
 @Override public void updateAimlessModeStatistics(com.amap.api.navi.model.AimLessModeStat a0) {}
 @Override public void updateAimlessModeCongestionInfo(com.amap.api.navi.model.AimLessModeCongestionInfo a0) {}
 @Override public void onPlayRing(int a0) {}
 @Override public void onCalculateRouteSuccess(com.amap.api.navi.model.AMapCalcRouteResult a0) {}
 @Override public void onCalculateRouteFailure(com.amap.api.navi.model.AMapCalcRouteResult a0) {}
 @Override public void onNaviRouteNotify(com.amap.api.navi.model.AMapNaviRouteNotifyData a0) {}
 @Override public void onGpsSignalWeak(boolean a0) {}
}
