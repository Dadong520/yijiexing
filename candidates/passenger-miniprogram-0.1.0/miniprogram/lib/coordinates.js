'use strict';
const PI=Math.PI,A=6378245,EE=0.006693421622965943;
function point(lat,lng) {if(typeof lat!=='number'||typeof lng!=='number'||!Number.isFinite(lat)||!Number.isFinite(lng)||lat===0||lng===0||Math.abs(lat)>90||Math.abs(lng)>180) throw new Error('地图坐标缺失或无效');return {latitude:lat,longitude:lng};}
function outside(lat,lng){return lng<72.004||lng>137.8347||lat<0.8293||lat>55.8271;}
function delta(lat,lng){let x=lng-105,y=lat-35;let a=-100+2*x+3*y+.2*y*y+.1*x*y+.2*Math.sqrt(Math.abs(x));a+=(20*Math.sin(6*x*PI)+20*Math.sin(2*x*PI))*2/3;a+=(20*Math.sin(y*PI)+40*Math.sin(y/3*PI))*2/3;a+=(160*Math.sin(y/12*PI)+320*Math.sin(y*PI/30))*2/3;let b=300+x+2*y+.1*x*x+.1*x*y+.1*Math.sqrt(Math.abs(x));b+=(20*Math.sin(6*x*PI)+20*Math.sin(2*x*PI))*2/3;b+=(20*Math.sin(x*PI)+40*Math.sin(x/3*PI))*2/3;b+=(150*Math.sin(x/12*PI)+300*Math.sin(x/30*PI))*2/3;const rad=lat/180*PI,magic=1-EE*Math.sin(rad)**2,s=Math.sqrt(magic);return [a*180/((A*(1-EE))/(magic*s)*PI),b*180/(A/s*Math.cos(rad)*PI)];}
function wgs84ToGcj02(lat,lng){const p=point(lat,lng);if(outside(lat,lng))return p;const d=delta(lat,lng);return point(lat+d[0],lng+d[1]);}
function gcj02ToWgs84(lat,lng){const p=point(lat,lng);if(outside(lat,lng))return p;let a=lat,b=lng;for(let i=0;i<10;i++){const q=wgs84ToGcj02(a,b);a-=q.latitude-lat;b-=q.longitude-lng;}return point(a,b);}
module.exports={point,wgs84ToGcj02,gcj02ToWgs84};
