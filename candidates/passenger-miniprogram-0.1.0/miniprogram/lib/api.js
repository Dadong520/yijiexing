'use strict';
const PATHS = {'code':['POST','/api/passenger/auth/code'],login:['POST','/api/passenger/auth/login'],state:['GET','/api/passenger/state'],action:['POST','/api/passenger/action'],logout:['POST','/api/passenger/auth/logout']};
const ORIGIN='https://driver-demo-491829048305.us-west1.run.app';
function createApi(wx, options={}) {
 const allowed=options.allowedOrigins||[ORIGIN];
  return {request(operation, data, credentials) {
    const route = PATHS[operation];
    if (!route) return Promise.reject(new Error('不允许的乘客接口'));
    const {baseUrl, access, token} = credentials || {};
    if (!allowed.includes(baseUrl) || typeof baseUrl !== 'string' || !/^https:\/\/[a-z0-9.-]+(?::\d+)?$/i.test(baseUrl)) return Promise.reject(new Error('请输入独立体验服务的 HTTPS 域名，不含路径'));
    if (!access || /[\r\n]/.test(access)) return Promise.reject(new Error('请填写体验访问码'));
    const header = {'Content-Type':'application/json','X-Demo-Access':access};
    if (token && !['code','login'].includes(operation)) header.Authorization = 'Bearer '+token;
    return new Promise((resolve,reject) => wx.request({url:baseUrl+route[1]+(operation==='state'?'?client=MINIPROGRAM':''),method:route[0],data:route[0]==='GET'?undefined:(['login','action'].includes(operation)?Object.assign({},data,{client:'MINIPROGRAM'}):data),header,timeout:20000,
      success(res) {
        const body=res.data, successful=res.statusCode>=200 && res.statusCode<300;
        const envelope=body && typeof body==='object' && !Array.isArray(body) && typeof body.code==='number';
        const validData=envelope && body.data && typeof body.data==='object' && !Array.isArray(body.data);
        if (successful && body && body.code===0 && validData) resolve(body.data);
        else {const e=new Error(envelope && body.message || '服务响应未确认，请使用原请求重试');e.status=res.statusCode;e.code=envelope && body.code;e.details=envelope && body.details;
          // A malformed success may follow a committed mutation. Never discard its request ID.
          e.uncertain=res.statusCode>=500 || !envelope || (successful && body.code===0 && !validData);reject(e);}
      },
      fail() {const e=new Error('网络结果未确认，请使用原请求重试');e.uncertain=true;reject(e);}
    }));
  }};
}
module.exports={createApi,ORIGIN};
