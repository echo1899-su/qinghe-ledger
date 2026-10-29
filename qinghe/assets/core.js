(function(root,factory){const api=factory();if(typeof module==='object')module.exports=api;else root.Ledger=api;})(typeof globalThis!=='undefined'?globalThis:this,function(){
'use strict';
const VERSION=1, LIMIT=99999999999;
const Scenarios=typeof module==='object'?require('./parallel-core.js'):globalThis.Parallel;
const uid=()=> 'q'+Date.now().toString(36)+Math.random().toString(36).slice(2,11);
const clone=o=>JSON.parse(JSON.stringify(o));
const today=()=>{const d=new Date();return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');};
function amount(text,signed=false){
 let s=String(text??'').trim().replace(/[￥¥\s]/g,'');
 if(/[,，]/.test(s)&&!(/^-?\d{1,3}([,，]\d{3})+(\.\d{1,2})?$/).test(s))throw Error('千位分隔符格式不正确');
 s=s.replace(/[,，]/g,'');if(s[0]==='.')s='0'+s;
 if(!(signed?/^-?\d{1,10}(\.\d{1,2})?$/:/^\d{1,10}(\.\d{1,2})?$/).test(s))throw Error('金额请填写数字，最多两位小数');
 const neg=s[0]==='-',p=s.replace('-','').split('.');const n=(Number(p[0])*100+Number((p[1]||'').padEnd(2,'0')))*(neg?-1:1);
 if(!Number.isSafeInteger(n)||Math.abs(n)>LIMIT)throw Error('金额超出支持范围');return n;
}
const format=n=>{const k=Number(n);if(!Number.isFinite(k))return'0.00';const neg=k<0,v=Math.abs(k)/100,c=v.toFixed(2),dot=c.length-3,int=c.slice(0,dot).replace(/\B(?=(\d{3})+(?!\d))/g,',');return (neg?'-':'')+int+c.slice(dot);};
function validDate(s){if(!/^\d{4}-\d{2}-\d{2}$/.test(s))return false;const [y,m,d]=s.split('-').map(Number),x=new Date(y,m-1,d);return y>=1900&&y<=2200&&x.getFullYear()===y&&x.getMonth()===m-1&&x.getDate()===d;}
function parseDate(v){
 let s=String(v??'').trim();if(/^\d+(\.\d+)?$/.test(s)&&+s>20000&&+s<110000){const d=new Date(Date.UTC(1899,11,30)+Math.floor(+s)*86400000);s=d.toISOString().slice(0,10);}
 const m=s.match(/^(\d{4})[-/年.](\d{1,2})[-/月.](\d{1,2})/);if(m)s=m[1]+'-'+m[2].padStart(2,'0')+'-'+m[3].padStart(2,'0');
 if(!validDate(s))throw Error('无法识别日期：'+String(v).slice(0,25));return s;
}
function nextDate(date,repeat,anchor){
 const [y,m,d]=date.split('-').map(Number),x=new Date(y,m-1,d,12);
 if(repeat==='daily')x.setDate(d+1);else if(repeat==='weekly')x.setDate(d+7);else if(repeat==='yearly'){x.setDate(1);x.setFullYear(y+1);x.setDate(Math.min(anchor||d,new Date(y+1,m,0).getDate()));}
 else {x.setDate(1);x.setMonth(m);x.setDate(Math.min(anchor||d,new Date(x.getFullYear(),x.getMonth()+1,0).getDate()));}
 return x.getFullYear()+'-'+String(x.getMonth()+1).padStart(2,'0')+'-'+String(x.getDate()).padStart(2,'0');
}
function initial(){
 const cats=[['餐饮','🍜'],['购物','🛍'],['交通','🚇'],['住房','🏠'],['日用','🧻'],['娱乐','🎮'],['医疗','💊'],['学习','📚'],['人情','🎁'],['旅行','🧳'],['通讯','📱'],['宠物','🐾'],['其他支出','◌']].map((x,i)=>({id:'e'+i,name:x[0],icon:x[1],type:'expense'}));
 cats.push(...[['工资','💼'],['奖金','✨'],['兼职','🛠'],['理财','🌱'],['其他收入','☀']].map((x,i)=>({id:'i'+i,name:x[0],icon:x[1],type:'income'})));
 return {schema:VERSION,books:[{id:'main',name:'日常账本'}],accounts:[{id:'wechat',name:'微信',icon:'💬',opening:0},{id:'alipay',name:'支付宝',icon:'💠',opening:0},{id:'bank',name:'银行卡',icon:'💳',opening:0},{id:'cash',name:'现金',icon:'👛',opening:0}],categories:cats,transactions:[],budgets:[],templates:[],rules:[],trash:[],settings:{theme:'light',hideAmounts:false,reminder:''}};
}
function checkText(v,max=200){return typeof v==='string'&&v.length<=max;}
function validate(s){
 if(!s||s.schema!==VERSION)throw Error('不是青禾账本支持的备份版本');
 for(const k of ['books','accounts','categories','transactions','budgets','templates','rules','trash'])if(!Array.isArray(s[k])||s[k].length>100000)throw Error('备份数据结构异常：'+k);
 if(!s.books.length||!s.accounts.length)throw Error('至少需要一个账本和一个账户');
 for(const key of ['books','accounts','categories','templates','rules']){const ids=new Set();for(const x of s[key]){if(!x||!checkText(x.id,100)||!x.id||ids.has(x.id))throw Error('编号重复或无效：'+key);ids.add(x.id);if(key!=='rules'&&(!checkText(x.name,60)||!x.name.trim()))throw Error('名称无效：'+key);}}
 for(const a of s.accounts)if(!Number.isSafeInteger(a.opening)||Math.abs(a.opening)>LIMIT)throw Error('账户初始金额无效');
 for(const c of s.categories)if(!['income','expense'].includes(c.type)||!checkText(c.icon,16))throw Error('分类信息无效');
 const books=new Set(s.books.map(x=>x.id)),accounts=new Set(s.accounts.map(x=>x.id)),cats=new Map(s.categories.map(x=>[x.id,x]));
 const ids=new Set();
 function transaction(t,withId=true){
  if(!t||!['income','expense','transfer'].includes(t.type)||!validDate(t.date)||!Number.isSafeInteger(t.amount)||t.amount<=0||t.amount>LIMIT||!books.has(t.book)||!accounts.has(t.account))throw Error('账目金额、日期或账户无效');
  if(withId){if(!checkText(t.id,100)||!t.id||ids.has(t.id))throw Error('账目编号重复或无效');ids.add(t.id);}
  if(t.type==='transfer'){if(!accounts.has(t.to)||t.to===t.account)throw Error('转入和转出账户必须不同');}else if(!cats.has(t.category)||cats.get(t.category).type!==t.type)throw Error('账目分类无效');
  if(!checkText(t.note,2000)||!checkText(t.tags,300)||!checkText(t.member||'',60)||!['','pending','done'].includes(t.reimburse||''))throw Error('账目备注信息无效');
  if(t.image&&(!/^data:image\/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+$/.test(t.image)||t.image.length>1600000))throw Error('图片格式或大小无效');
 }
 for(const t of s.transactions)transaction(t);
 for(const t of s.trash){transaction(t);if(!Number.isFinite(t.deletedAt))throw Error('回收站数据无效');}
 for(const t of s.templates)transaction(t.record,false);
 for(const r of s.rules){transaction(r.record,false);if(!validDate(r.next)||!['daily','weekly','monthly','yearly'].includes(r.repeat)||!Number.isInteger(r.anchor)||r.anchor<1||r.anchor>31||typeof r.enabled!=='boolean')throw Error('定时记账规则无效');}
 const bkeys=new Set();for(const b of s.budgets){const k=b.month+'|'+b.book+'|'+b.category;if(!/^\d{4}-(0[1-9]|1[0-2])$/.test(b.month)||!Number.isSafeInteger(b.amount)||b.amount<0||b.amount>LIMIT||(b.book!=='all'&&!books.has(b.book))||(b.category!=='all'&&cats.get(b.category)?.type!=='expense')||bkeys.has(k))throw Error('预算数据无效');bkeys.add(k);}
 if(!s.settings||!['light','dark','forest'].includes(s.settings.theme)||typeof s.settings.hideAmounts!=='boolean')throw Error('设置数据无效');
 if(s.scenarios!==undefined)Scenarios.validatePlans(s.scenarios);
 return s;
}
function balance(s,id,through='9999-99-99'){let n=s.accounts.find(a=>a.id===id)?.opening||0;for(const t of s.transactions){if(t.date>through)continue;if(t.account===id)n+=t.type==='income'?t.amount:-t.amount;if(t.type==='transfer'&&t.to===id)n+=t.amount;}return n;}
function filtered(s,{month='',book='all',type='all',category='all',query='',from='',to='',account='all',reimburse='all'}={}){
 const q=query.trim().toLowerCase();return s.transactions.filter(t=>(!month||t.date.startsWith(month))&&(book==='all'||t.book===book)&&(type==='all'||t.type===type)&&(category==='all'||t.category===category)&&(!from||t.date>=from)&&(!to||t.date<=to)&&(account==='all'||t.account===account||t.to===account)&&(reimburse==='all'||(t.reimburse||'')===reimburse)&&(!q||[t.note,t.tags,t.member,format(t.amount),(t.amount/100).toString(),s.categories.find(c=>c.id===t.category)?.name,s.accounts.find(a=>a.id===t.account)?.name,s.books.find(b=>b.id===t.book)?.name].join(' ').toLowerCase().includes(q))).sort((a,b)=>b.date.localeCompare(a.date)||(b.created||0)-(a.created||0));
}
function summary(rows){let income=0,expense=0;for(const t of rows){if(t.type==='income')income+=t.amount;if(t.type==='expense')expense+=t.amount;}return {income,expense,net:income-expense,count:rows.length};}
function groups(rows,type='expense'){const m=new Map();for(const t of rows)if(t.type===type)m.set(t.category,(m.get(t.category)||0)+t.amount);return [...m].map(([id,total])=>({id,total})).sort((a,b)=>b.total-a.total);}
function budget(s,month,book,category='all'){return s.budgets.find(b=>b.month===month&&b.book===book&&b.category===category)?.amount||0;}
function runRules(s,until=today()){
 let count=0;for(const r of s.rules){if(!r.enabled)continue;let n=0;while(r.next<=until&&n<3660){const id='r_'+r.id+'_'+r.next;if(!s.transactions.some(t=>t.id===id)&&!s.trash.some(t=>t.id===id)){s.transactions.push({...clone(r.record),id,date:r.next,created:Date.now()});count++;}r.next=nextDate(r.next,r.repeat,r.anchor);n++;}if(n===3660&&r.next<=until)throw Error('定时记账跨度过大，请调整开始日期');}return count;
}
function parseCSV(text){
 text=String(text).replace(/^\uFEFF/,'');const sample=text.slice(0,16000).replace(/"(?:[^"]|"")*"/g,''),lines=sample.split(/\r?\n/).slice(0,40),score=d=>lines.reduce((n,l)=>n+(l.split(d).length-1),0);const delim=['\t',',',';'].sort((a,b)=>score(b)-score(a))[0];
 const rows=[];let row=[],v='',quoted=false;
 for(let i=0;i<text.length;i++){const c=text[i];if(c==='"'){if(quoted&&text[i+1]==='"'){v+='"';i++;}else if(!quoted&&v.length){v+='"';}else quoted=!quoted;}
 else if(c===delim&&!quoted){row.push(v);v='';}else if((c==='\n'||c==='\r')&&!quoted){if(c==='\r'&&text[i+1]==='\n')i++;row.push(v);if(row.some(x=>x.trim()))rows.push(row);row=[];v='';}else v+=c;
 if(rows.length>100000||v.length>2000000)throw Error('文件太大，请分批导入');}
 if(quoted)throw Error('CSV 引号未闭合，请检查导出文件');row.push(v);if(row.some(x=>x.trim()))rows.push(row);return rows;
}
const aliases={date:['日期','时间','交易时间','记账时间','交易日期','date'],amount:['金额','金额元','交易金额','收支金额','amount'],type:['类型','收支','收支类型','收支方式','type'],category:['分类','类别','一级分类','收支分类','category'],note:['备注','说明','商品说明','商品','交易内容','note'],account:['账户','资产','付款方式','支付方式','account'],to:['转入账户','toaccount'],book:['账本','账本名称','book'],tags:['标签','tags'],member:['成员','记账人','member'],id:['记录编号','账目编号','id'],reimburse:['报销','报销状态']};
const headerKey=s=>String(s).toLowerCase().replace(/[\s()（）\[\]￥¥/：:_-]/g,'');
function detectColumns(rows){let best=null;for(let n=0;n<Math.min(rows.length,40);n++){const map={};for(const [key,vals]of Object.entries(aliases))map[key]=rows[n].findIndex(h=>vals.includes(headerKey(h)));const score=(map.date>=0?5:0)+(map.amount>=0?5:0)+(map.type>=0?2:0)+(map.category>=0?1:0);if(!best||score>best.score)best={header:n,map,score};}return best||{header:0,map:{},score:0};}
function fingerprint(t){return JSON.stringify([t.date,t.type,t.amount,t.category,t.account,t.to||'',t.book,t.note,t.tags,t.member||'']);}
function importRows(original,rows,{header=0,map=detectColumns(rows).map,defaultBook='main',defaultAccount='cash',skipDuplicates=true}={}){
 if(map.date<0||map.amount<0)throw Error('请指定日期和金额列');
 const s=clone(original),existing=new Map(),usedIds=new Set(s.transactions.map(t=>t.id));
 for(const t of s.transactions){const f=fingerprint(t);existing.set(f,(existing.get(f)||0)+1);}
 let added=0,duplicates=0;const errors=[];
 const lookup=(list,name,make)=>{let item=list.find(x=>x.name===name);if(!item){item=make();list.push(item);}return item.id;};
 for(let i=header+1;i<rows.length;i++){const r=rows[i];if(!r.some(v=>String(v).trim()))continue;const cell=k=>map[k]>=0?String(r[map[k]]??'').trim():'';const plain=k=>{const x=cell(k);return x.startsWith("'")&&/^['=+\-@\t\r]/.test(x.slice(1))?x.slice(1):x;};
  const lengths=[s.books.length,s.accounts.length,s.categories.length];
  try{
   const date=parseDate(cell('date')),raw=amount(cell('amount'),true),tv=cell('type');if(!raw)throw Error('金额为零');
   let type;if(/^(支出|expense|支)$/i.test(tv))type='expense';else if(/^(收入|income|收)$/i.test(tv))type='income';else if(/^(转账|transfer)$/i.test(tv))type='transfer';else if(!tv)type=raw<0?'expense':'expense';else throw Error('无法识别收支类型：'+tv);
   const book=plain('book')?lookup(s.books,plain('book'),()=>({id:uid(),name:plain('book')})):defaultBook;
   const account=plain('account')?lookup(s.accounts,plain('account'),()=>({id:uid(),name:plain('account'),icon:'💳',opening:0})):defaultAccount;
   const name=plain('category')||(type==='income'?'其他收入':'其他支出');let c=s.categories.find(x=>x.name===name&&x.type===type);
   if(type!=='transfer'&&!c){c={id:uid(),name,icon:type==='income'?'☀':'◌',type};s.categories.push(c);}
   let to='';if(type==='transfer'){if(!plain('to'))throw Error('转账缺少转入账户');to=lookup(s.accounts,plain('to'),()=>({id:uid(),name:plain('to'),icon:'💳',opening:0}));if(to===account)throw Error('转账账户相同');}
   
   const t={id:plain('id')||uid(),date,type,amount:Math.abs(raw),book,account,to,category:c?.id||'',note:plain('note'),tags:plain('tags'),member:plain('member'),reimburse:/^(待报销|pending)$/.test(cell('reimburse'))?'pending':/^(已报销|done)$/.test(cell('reimburse'))?'done':'',created:Date.now()+i};
   if([t.note,t.tags,t.member].some(x=>x.length>2000)||name.length>60||s.books.find(x=>x.id===book).name.length>60||s.accounts.find(x=>x.id===account).name.length>60||t.tags.length>300||t.member.length>60||t.id.length>100)throw Error('文本过长');
   const f=fingerprint(t);if(skipDuplicates&&((plain('id')&&usedIds.has(t.id))||(!plain('id')&&(existing.get(f)||0)>0))){duplicates++;if(existing.get(f)>0)existing.set(f,existing.get(f)-1);continue;}
   if(usedIds.has(t.id))t.id=uid();usedIds.add(t.id);s.transactions.push(t);added++;
  }catch(e){s.books.length=lengths[0];s.accounts.length=lengths[1];s.categories.length=lengths[2];errors.push({row:i+1,message:e.message});}
 }
 validate(s);return {state:s,added,duplicates,errors};
}
function exportCSV(s,rows=s.transactions){
 const safe=v=>{let x=String(v??'');if(/^['=+\-@\t\r]/.test(x))x="'"+x;return '"'+x.replace(/"/g,'""')+'"';};const name=(list,id)=>list.find(x=>x.id===id)?.name||'';
 const table=[['记录编号','日期','类型','金额','分类','账户','转入账户','账本','备注','标签','成员','报销状态']];
 for(const t of rows)table.push([t.id,t.date,{income:'收入',expense:'支出',transfer:'转账'}[t.type],(t.amount/100).toFixed(2),name(s.categories,t.category),name(s.accounts,t.account),name(s.accounts,t.to),name(s.books,t.book),t.note,t.tags,t.member||'',{pending:'待报销',done:'已报销'}[t.reimburse]||'']);
 return '\uFEFF'+table.map(row=>row.map(safe).join(',')).join('\r\n');
}
/** 把金额换算成有画面感的说法：¥45 → 3杯奶茶 */
function compare(n){const y=Number(n)/100;if(!(y>0))return '';if(y<6)return (Math.round(y*10)/10)+'元';const opts=[[15,'杯奶茶'],[20,'顿外卖'],[5,'趟地铁'],[40,'张电影票']];for(const o of opts){const v=y/o[0];if(v>=1&&v<100)return (v>=10?Math.round(v):Math.round(v*10)/10)+o[1];}const h=y/30;return (h>=10?Math.round(h):Math.round(h*10)/10)+'小时工资';}
return {VERSION,LIMIT,uid,clone,today,amount,format,compare,validDate,parseDate,nextDate,initial,validate,balance,filtered,summary,groups,budget,runRules,parseCSV,detectColumns,importRows,exportCSV};
});
