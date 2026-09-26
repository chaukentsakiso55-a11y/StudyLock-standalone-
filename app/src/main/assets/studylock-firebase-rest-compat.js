(()=>{
if(window.__studyLockRestFirebaseCompat||window.firebase?.__studyLockRestCompat)return;
window.__studyLockRestFirebaseCompat=true;
const apps=[];
const POLL_MS=2500;
const request=async(url,opts={})=>{const r=await fetch(url,opts);let data={};try{data=await r.json()}catch(_){}if(!r.ok){const msg=data?.error?.message||data?.error?.status||`HTTP ${r.status}`;if(String(msg).includes('OPERATION_NOT_ALLOWED'))throw new Error('Firebase Anonymous Authentication is disabled.');if(String(msg).includes('PERMISSION_DENIED'))throw new Error('Firebase permission denied.');throw new Error(msg)}return data};
function toValue(v){if(v===null)return{nullValue:null};if(Array.isArray(v))return{arrayValue:{values:v.map(toValue)}};switch(typeof v){case'boolean':return{booleanValue:v};case'number':return Number.isInteger(v)?{integerValue:String(v)}:{doubleValue:v};case'object':{const fields={};Object.entries(v).forEach(([k,x])=>fields[k]=toValue(x));return{mapValue:{fields}}}default:return{stringValue:String(v)}}}
function fromValue(v){if(!v)return null;if('nullValue'in v)return null;if('stringValue'in v)return v.stringValue;if('booleanValue'in v)return!!v.booleanValue;if('integerValue'in v)return Number(v.integerValue);if('doubleValue'in v)return Number(v.doubleValue);if('timestampValue'in v)return v.timestampValue;if('arrayValue'in v)return(v.arrayValue.values||[]).map(fromValue);if('mapValue'in v){const o={};Object.entries(v.mapValue.fields||{}).forEach(([k,x])=>o[k]=fromValue(x));return o}return null}
const enc=p=>String(p||'').split('/').filter(Boolean).map(encodeURIComponent).join('/');
function fields(data){const out={};Object.entries(data||{}).forEach(([k,v])=>out[k]=toValue(v));return out}
function decode(raw){const out={};Object.entries(raw?.fields||{}).forEach(([k,v])=>out[k]=fromValue(v));return out}
class Snap{constructor(ref,raw){this.ref=ref;this.id=ref.id;this.exists=!!raw;this._raw=raw}data(){return this._raw?decode(this._raw):undefined}}
class QuerySnap{constructor(docs,changes){this.docs=docs;this.empty=!docs.length;this.size=docs.length;this._changes=changes||docs.map(doc=>({type:'added',doc}))}docChanges(){return this._changes}}
class Auth{
 constructor(app){this.app=app;this.currentUser=null;this.tokenValue='';this.refresh='';this.exp=0;this.key=`studylock_fb_auth_${app.options.projectId}`;try{const x=JSON.parse(localStorage.getItem(this.key)||'null');if(x?.uid&&x?.refresh){this.currentUser={uid:x.uid};this.refresh=x.refresh;this.tokenValue=x.token||'';this.exp=Number(x.exp||0)}}catch(_){}}
 save(){try{localStorage.setItem(this.key,JSON.stringify({uid:this.currentUser?.uid||'',refresh:this.refresh,token:this.tokenValue,exp:this.exp}))}catch(_){}}
 async signInAnonymously(){if(this.refresh){try{await this.refreshToken();return{user:this.currentUser}}catch(_){this.refresh='';this.tokenValue='';this.exp=0}}const d=await request(`https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${encodeURIComponent(this.app.options.apiKey)}`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({returnSecureToken:true})});this.currentUser={uid:d.localId};this.tokenValue=d.idToken;this.refresh=d.refreshToken;this.exp=Date.now()+Math.max(60,Number(d.expiresIn||3600)-120)*1000;this.save();return{user:this.currentUser}}
 async refreshToken(){const d=await request(`https://securetoken.googleapis.com/v1/token?key=${encodeURIComponent(this.app.options.apiKey)}`,{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:`grant_type=refresh_token&refresh_token=${encodeURIComponent(this.refresh)}`});this.currentUser={uid:d.user_id};this.tokenValue=d.id_token;this.refresh=d.refresh_token||this.refresh;this.exp=Date.now()+Math.max(60,Number(d.expires_in||3600)-120)*1000;this.save()}
 async token(){if(!this.currentUser)await this.signInAnonymously();if(!this.tokenValue||Date.now()>this.exp)await this.refreshToken();return this.tokenValue}
}
class Firestore{
 constructor(app){this.app=app;this.auth=app.auth();this.base=`https://firestore.googleapis.com/v1/projects/${encodeURIComponent(app.options.projectId)}/databases/(default)/documents`}
 settings(){}
 collection(path){return new Collection(this,path)}
 async headers(){return{'Content-Type':'application/json','Authorization':`Bearer ${await this.auth.token()}`}}
 async get(path){try{return await request(`${this.base}/${enc(path)}`,{headers:await this.headers()})}catch(e){if(String(e.message).includes('NOT_FOUND'))return null;throw e}}
 async set(path,data,merge){let url=`${this.base}/${enc(path)}`;if(merge){const keys=Object.keys(data||{});if(keys.length)url+='?'+keys.map(k=>'updateMask.fieldPaths='+encodeURIComponent(k)).join('&')}return request(url,{method:'PATCH',headers:await this.headers(),body:JSON.stringify({fields:fields(data)})})}
 async del(path){try{await request(`${this.base}/${enc(path)}`,{method:'DELETE',headers:await this.headers()})}catch(e){if(!String(e.message).includes('NOT_FOUND'))throw e}}
 async list(path){let out=[],token='';do{let url=`${this.base}/${enc(path)}?pageSize=200`;if(token)url+=`&pageToken=${encodeURIComponent(token)}`;const d=await request(url,{headers:await this.headers()});out=out.concat(d.documents||[]);token=d.nextPageToken||''}while(token&&out.length<1000);return out}
}
class Doc{
 constructor(db,path){this.db=db;this.path=path;this.id=String(path).split('/').filter(Boolean).pop()||''}
 collection(name){return new Collection(this.db,`${this.path}/${name}`)}
 async get(){return new Snap(this,await this.db.get(this.path))}
 async set(data,opts={}){await this.db.set(this.path,JSON.parse(JSON.stringify(data)),!!opts?.merge);return this}
 async delete(){await this.db.del(this.path)}
 onSnapshot(next,error){let closed=false,last='';const tick=async()=>{if(closed)return;try{const raw=await this.db.get(this.path);const sig=JSON.stringify(raw?.fields||null);if(sig!==last){last=sig;next(new Snap(this,raw))}}catch(e){error?.(e)}finally{if(!closed)setTimeout(tick,POLL_MS)}};tick();return()=>{closed=true}}
}
class Query{
 constructor(col,ops=[]){this.col=col;this.ops=ops}
 where(field,op,value){return new Query(this.col,this.ops.concat([{t:'where',field,op,value}]))}
 orderBy(field,dir='asc'){return new Query(this.col,this.ops.concat([{t:'order',field,dir}]))}
 limit(n){return new Query(this.col,this.ops.concat([{t:'limit',n:Number(n)}]))}
 async fetch(prev=new Map()){let raws=await this.col.db.list(this.col.path);let rows=raws.map(raw=>{const id=String(raw.name||'').split('/').pop();return new Snap(new Doc(this.col.db,`${this.col.path}/${id}`),raw)});for(const o of this.ops){if(o.t==='where')rows=rows.filter(d=>{const v=d.data()?.[o.field];if(o.op==='>=')return v>=o.value;if(o.op==='<=')return v<=o.value;if(o.op==='==')return v===o.value;return true});if(o.t==='order')rows.sort((a,b)=>{const av=a.data()?.[o.field],bv=b.data()?.[o.field];const x=av===bv?0:(av>bv?1:-1);return o.dir==='desc'?-x:x});if(o.t==='limit')rows=rows.slice(0,o.n)}const cur=new Map(rows.map(d=>[d.id,JSON.stringify(d.data())]));const changes=[];rows.forEach(d=>{const s=cur.get(d.id);if(!prev.has(d.id))changes.push({type:'added',doc:d});else if(prev.get(d.id)!==s)changes.push({type:'modified',doc:d})});return{snap:new QuerySnap(rows,changes),cur}}
 onSnapshot(next,error){let closed=false,prev=new Map();const tick=async()=>{if(closed)return;try{const r=await this.fetch(prev);if(!prev.size||r.snap.docChanges().length)next(r.snap);prev=r.cur}catch(e){error?.(e)}finally{if(!closed)setTimeout(tick,POLL_MS)}};tick();return()=>{closed=true}}
}
class Collection extends Query{
 constructor(db,path){super(null,[]);this.db=db;this.path=path;this.col=this}
 doc(id){return new Doc(this.db,`${this.path}/${id}`)}
 async add(data){const ref=this.doc(`${Date.now().toString(36)}${Math.random().toString(36).slice(2,12)}`);await ref.set(data);return ref}
 where(field,op,value){return new Query(this,[{t:'where',field,op,value}])}
 orderBy(field,dir='asc'){return new Query(this,[{t:'order',field,dir}])}
 limit(n){return new Query(this,[{t:'limit',n:Number(n)}])}
 onSnapshot(next,error){return new Query(this,[]).onSnapshot(next,error)}
}
class App{constructor(options,name){this.options=options;this.name=name||'[DEFAULT]';this.a=new Auth(this);this.d=new Firestore(this)}auth(){return this.a}firestore(){return this.d}}
window.firebase={__studyLockRestCompat:true,apps,initializeApp(options,name){const found=apps.find(a=>a.name===(name||'[DEFAULT]'));if(found)return found;const app=new App(options,name);apps.push(app);return app},auth:true,firestore:true};
})();