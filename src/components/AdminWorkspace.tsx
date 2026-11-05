import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { supabase } from '../lib/supabase';
import { ShieldCheck, LayoutDashboard, BookOpen, Users, Flag, Link2, HardDrive, Plus, RefreshCw, Save, Trash2, Pencil, X, Search, Check, AlertCircle, Download, Youtube, ListVideo, Radar, CheckCircle2, XCircle } from 'lucide-react';

type Tab = 'overview' | 'catalogue' | 'characters' | 'videos' | 'moderation' | 'reports' | 'settings' | 'storage';
type TitleRow = { id:string; name:string; media_type:string; status:string; cover_url:string|null; banner_url:string|null; synopsis:string|null; genres:string[]|null; themes:string[]|null; alternative_names:string[]|null; release_year:number|null; rating:number|null; chapters:string|null; episodes:string|null; author:string|null; artist:string|null; studio:string|null; country:string|null; source_url:string|null; trailer_url:string|null; read_url:string|null; is_featured:boolean; is_trending:boolean; popularity:number|null };
type CharacterRow = { id:string; title_id:string; name:string; role:string|null; description:string|null; image_url:string|null };
type VideoRow = { id:string; title_id:string|null; video_url:string; platform:string; video_title:string|null; creator_name:string|null; creator_avatar_url:string|null; action_type:string|null; action_url:string|null; sort_order:number|null };
type VideoReportRow = { id:string; video_id:string; video_url:string; video_title:string|null; reason:string; details:string|null; created_at:string };
type VideoSourceRow = { id:string; source_type:'channel'|'playlist'; source_key:string; label:string; enabled:boolean; last_checked_at:string|null; created_at:string };
type DiscoveredVideoRow = { id:string; video_id:string; video_url:string; video_title:string; creator_name:string|null; creator_avatar_url?:string|null; thumbnail_url:string|null; source_id:string|null; duration?:string|null; status:'pending'|'approved'|'rejected'; discovered_at:string; category?:string|null; relevance_score?:number|null; description?:string|null; view_count?:number|null; published_at?:string|null };
type ReportRow = { id:string; comment_id:string; reporter_id:string; reason:string; details:string|null; created_at:string };
const blankTitle = { name:'', media_type:'Manga', status:'Ongoing', cover_url:'', banner_url:'', synopsis:'', genres:'', themes:'', alternative_names:'', release_year:'', rating:'', chapters:'', episodes:'', author:'', artist:'', studio:'', country:'', source_url:'', trailer_url:'', read_url:'', is_featured:false, is_trending:false };
const blankCharacter = { title_id:'', name:'', role:'Main', description:'', image_url:'' };
const blankVideo = { title_id:'', video_url:'', platform:'youtube', video_title:'', creator_name:'', creator_avatar_url:'', action_type:'watch', action_url:'', sort_order:'0' };
const toList = (v:string) => v.split(',').map(x=>x.trim()).filter(Boolean);
const errText = (e:unknown) => e instanceof Error ? e.message : 'Something went wrong.';

export function AdminWorkspace() {
  const [tab,setTab] = useState<Tab>('overview');
  const [checking,setChecking] = useState(true);
  const [admin,setAdmin] = useState(false);
  const [userEmail,setUserEmail] = useState('');
  const [error,setError] = useState('');
  const [notice,setNotice] = useState('');
  const [busy,setBusy] = useState(false);
  const [titles,setTitles] = useState<TitleRow[]>([]);
  const [titleTotal,setTitleTotal] = useState(0);
  const [titleQuery,setTitleQuery] = useState('');
  const [titleForm,setTitleForm] = useState<any>(blankTitle);
  const [editingTitle,setEditingTitle] = useState<string|null>(null);
  const [characters,setCharacters] = useState<CharacterRow[]>([]);
  const [characterForm,setCharacterForm] = useState<any>(blankCharacter);
  const [videos,setVideos] = useState<VideoRow[]>([]);
  const [videoForm,setVideoForm] = useState<any>(blankVideo);
  const [shortForm,setShortForm] = useState({video_url:'',video_title:'',creator_name:'',platform:'youtube' as 'youtube'|'instagram'|'tiktok'});
  const [shortBusy,setShortBusy] = useState(false);
  const [comments,setComments] = useState<any[]>([]);
  const [reports,setReports] = useState<ReportRow[]>([]);
  const [videoReports,setVideoReports] = useState<VideoReportRow[]>([]);
  const [videoReportsMissing,setVideoReportsMissing] = useState(false);
  const [videoSources,setVideoSources] = useState<VideoSourceRow[]>([]);
  const [discoveredVideos,setDiscoveredVideos] = useState<DiscoveredVideoRow[]>([]);
  const [selectedVideoIds,setSelectedVideoIds] = useState<string[]>([]);
  const [sourceForm,setSourceForm] = useState({source_type:'channel' as 'channel'|'playlist',source_key:'',label:''});
  const [approvalTitle,setApprovalTitle] = useState('');
  const [discoveryBusy,setDiscoveryBusy] = useState(false);
  const [socialLinks,setSocialLinks] = useState<any[]>([]);
  const [storage,setStorage] = useState<any[]>([]);
  const [aniSyncRunning,setAniSyncRunning] = useState(false);
  const [aniSyncProgress,setAniSyncProgress] = useState<any>(null);
  const aniSyncStopRef = useRef(false);

  const verify = useCallback(async () => {
    setChecking(true); setError('');
    const {data:{user},error:userError}=await supabase.auth.getUser();
    if(userError || !user){setAdmin(false);setUserEmail('');setChecking(false);return;}
    setUserEmail(user.email||'');
    // The verified owner account gets a UI fallback for embedded WebViews.
    // Database RLS still enforces every read/write, so this does not grant backend access.
    const VERIFIED_ADMIN_USER_IDS = ['4da14151-419b-4872-8e5b-6ec38185cdbf'];
    const isVerifiedOwner = VERIFIED_ADMIN_USER_IDS.includes(user.id);
    const {data,error:adminError}=await supabase.from('admin_users').select('user_id').eq('user_id',user.id).maybeSingle();
    if(adminError && !isVerifiedOwner){setError(adminError.message);setAdmin(false);}
    else {setError('');setAdmin(!!data || isVerifiedOwner);}
    setChecking(false);
  },[]);
  useEffect(()=>{void verify();},[verify]);

  const loadTitles = useCallback(async()=>{const {data,error,count}=await supabase.from('titles').select('*',{count:'exact'}).order('name').limit(1000);if(error)throw error;setTitles((data||[]) as TitleRow[]);setTitleTotal(count||0);},[]);
  const loadAniSyncProgress = useCallback(async()=>{const {data,error}=await supabase.from('site_settings').select('value').eq('key','anilist_catalogue_sync').maybeSingle();if(!error&&data?.value)setAniSyncProgress(data.value);},[]);
  const loadCharacters = useCallback(async()=>{const {data,error}=await supabase.from('characters').select('*').order('created_at',{ascending:false}).limit(300);if(error)throw error;setCharacters((data||[]) as CharacterRow[]);},[]);
  const loadVideos = useCallback(async()=>{const {data,error}=await supabase.from('related_videos').select('*').order('sort_order').order('created_at',{ascending:false}).limit(300);if(error)throw error;setVideos((data||[]) as VideoRow[]);},[]);
  const addShort = async (event:FormEvent) => {
    event.preventDefault(); setShortBusy(true); setError(''); setNotice('');
    try {
      const url = shortForm.video_url.trim();
      const parsed = new URL(url);
      const host = parsed.hostname.replace(/^www\./,'').toLowerCase();
      const platform = host === 'youtu.be' || host.endsWith('youtube.com') || host.endsWith('youtube-nocookie.com') ? 'youtube'
        : host === 'instagram.com' || host.endsWith('.instagram.com') || host === 'instagr.am' ? 'instagram'
        : host === 'tiktok.com' || host.endsWith('.tiktok.com') ? 'tiktok' : '';
      if (!platform || platform !== shortForm.platform) throw new Error('The URL does not match the selected platform.');
      if (platform === 'youtube' && !(/\/shorts\//.test(parsed.pathname) || host === 'youtu.be')) throw new Error('Use a YouTube Shorts URL (youtube.com/shorts/…).');
      if (platform === 'instagram' && !(/\/(reel|reels)\//.test(parsed.pathname))) throw new Error('Use an Instagram Reel URL.');
      if (platform === 'tiktok' && !/\/@[^/]+\/video\//.test(parsed.pathname)) throw new Error('Use a TikTok video URL.');
      const {error:insertError} = await supabase.from('related_videos').insert({
        title_id:null, video_url:url, platform, video_title:shortForm.video_title.trim() || 'Short video',
        creator_name:shortForm.creator_name.trim(), action_type:'watch', action_url:url, sort_order:0
      });
      if(insertError) throw insertError;
      setShortForm({video_url:'',video_title:'',creator_name:'',platform:'youtube'});
      await loadVideos();
      setNotice('Short added to the video catalogue.');
    } catch(e) { setError(errText(e)); } finally { setShortBusy(false); }
  };
  const loadDiscovery = useCallback(async()=>{const [sources,queue]=await Promise.all([supabase.from('video_discovery_sources').select('*').order('created_at',{ascending:false}).limit(100),supabase.from('video_discovery_queue').select('*').eq('status','pending').order('discovered_at',{ascending:false}).limit(1000)]);if(sources.error)throw sources.error;if(queue.error)throw queue.error;setVideoSources((sources.data||[]) as VideoSourceRow[]);setDiscoveredVideos((queue.data||[]) as DiscoveredVideoRow[]);setSelectedVideoIds([]);},[]);
  const loadModeration = useCallback(async()=>{const {data,error}=await supabase.from('community_comments').select('id,title_id,user_id,body,media_url,media_type,moderation_status,moderation_reason,created_at,kind,rating').eq('moderation_status','pending').order('created_at',{ascending:false}).limit(100);if(error)throw error;setComments(data||[]);},[]);
  const loadReports = useCallback(async()=>{const {data,error}=await supabase.from('community_comment_reports').select('*').order('created_at',{ascending:false}).limit(100);if(error)throw error;setReports((data||[]) as ReportRow[]);
    // Video reports live in their own table (see supabase/migrations). Missing table is not fatal.
    const vr=await supabase.from('video_reports').select('*').order('created_at',{ascending:false}).limit(100);
    if(vr.error){setVideoReportsMissing(true);setVideoReports([]);}else{setVideoReportsMissing(false);setVideoReports((vr.data||[]) as VideoReportRow[]);}
  },[]);
  const loadSocial = useCallback(async()=>{const {data,error}=await supabase.from('site_settings').select('value').eq('key','social_links').maybeSingle();if(error)throw error;let v=data?.value??[];if(typeof v==='string'){try{v=JSON.parse(v)}catch{v=[]}}setSocialLinks(Array.isArray(v)?v:[]);},[]);
  const loadStorage = useCallback(async()=>{const {data,error}=await supabase.rpc('admin_storage_usage');if(error)throw error;setStorage(data||[]);},[]);
  const refresh = useCallback(async()=>{if(!admin)return;setBusy(true);setError('');try{await Promise.all([loadTitles(),loadCharacters(),loadVideos(),loadModeration(),loadReports(),loadSocial(),loadStorage()]);}catch(e){setError(errText(e));}finally{setBusy(false);}},[admin,loadTitles,loadCharacters,loadVideos,loadModeration,loadReports,loadSocial,loadStorage]);
  useEffect(()=>{if(admin)void refresh();},[admin,refresh]);
  useEffect(()=>{if(admin)void loadAniSyncProgress();},[admin,loadAniSyncProgress]);
  useEffect(()=>{if(admin&&tab==='videos')void loadDiscovery().catch(e=>setError(errText(e)));},[admin,tab,loadDiscovery]);

  const saveTitle = async(e:FormEvent)=>{e.preventDefault();setBusy(true);setError('');try{const payload:any={...titleForm,genres:toList(titleForm.genres),themes:toList(titleForm.themes),alternative_names:toList(titleForm.alternative_names),release_year:titleForm.release_year?Number(titleForm.release_year):null,rating:titleForm.rating?Number(titleForm.rating):null};['cover_url','banner_url','synopsis','chapters','episodes','author','artist','studio','country','source_url','trailer_url','read_url'].forEach(k=>{if(payload[k]==='')payload[k]=null});const result=editingTitle?await supabase.from('titles').update(payload).eq('id',editingTitle):await supabase.from('titles').insert(payload);if(result.error)throw result.error;setNotice(editingTitle?'Entry updated on RykoCollection website.':'Entry added to the shared website catalogue.');setTitleForm(blankTitle);setEditingTitle(null);await loadTitles();}catch(e){setError(errText(e));}finally{setBusy(false);}};
  const editTitle=(t:TitleRow)=>{setEditingTitle(t.id);setTitleForm({...blankTitle,...t,genres:(t.genres||[]).join(', '),themes:(t.themes||[]).join(', '),alternative_names:(t.alternative_names||[]).join(', '),release_year:t.release_year?.toString()||'',rating:t.rating?.toString()||'',cover_url:t.cover_url||'',banner_url:t.banner_url||'',synopsis:t.synopsis||'',chapters:t.chapters||'',episodes:t.episodes||'',author:t.author||'',artist:t.artist||'',studio:t.studio||'',country:t.country||'',source_url:t.source_url||'',trailer_url:t.trailer_url||'',read_url:t.read_url||''});setTab('catalogue');};
  const deleteTitle=async(id:string)=>{if(!confirm('Delete this title from the shared website catalogue?'))return;setBusy(true);try{const {error}=await supabase.from('titles').delete().eq('id',id);if(error)throw error;await loadTitles();setNotice('Entry deleted from the shared catalogue.');}catch(e){setError(errText(e));}finally{setBusy(false);}};
  const saveCharacter=async(e:FormEvent)=>{e.preventDefault();setBusy(true);try{const {error}=await supabase.from('characters').insert({...characterForm,title_id:characterForm.title_id,name:characterForm.name.trim(),description:characterForm.description||null,image_url:characterForm.image_url||null});if(error)throw error;setCharacterForm(blankCharacter);await loadCharacters();setNotice('Character added to the website entry.');}catch(e){setError(errText(e));}finally{setBusy(false);}};
  const deleteCharacter=async(id:string)=>{if(!confirm('Delete this character profile?'))return;const {error}=await supabase.from('characters').delete().eq('id',id);if(error)setError(error.message);else await loadCharacters();};
  const saveVideo=async(e:FormEvent)=>{e.preventDefault();setBusy(true);setError('');setNotice('');try{
    const raw=videoForm.video_url.trim();let parsed:URL;try{parsed=new URL(raw);}catch{throw new Error('Enter a complete video URL beginning with https://');}
    const host=parsed.hostname.toLowerCase().replace(/^www\\./,'').replace(/^m\\./,'');
    let detected:'youtube'|'instagram'|'tiktok'|'dailymotion'|null=null;
    if(host==='youtube.com'||host.endsWith('.youtube.com')||host==='youtu.be'||host==='youtube-nocookie.com')detected='youtube';
    else if(host==='instagram.com'||host.endsWith('.instagram.com')||host==='instagr.am')detected='instagram';
    else if(host==='tiktok.com'||host.endsWith('.tiktok.com'))detected='tiktok';
    else if(host==='dailymotion.com'||host.endsWith('.dailymotion.com')||host==='dai.ly')detected='dailymotion';
    if(!detected)throw new Error('Supported links: YouTube, Instagram Reels, TikTok and Dailymotion.');
    if(detected!==videoForm.platform)throw new Error('This link belongs to '+detected+'. Select '+detected+' as the platform and save again.');
    if(detected==='instagram'&&!(['/reel/','/reels/','/p/'].some(path=>parsed.pathname.toLowerCase().includes(path))))throw new Error('Paste a direct Instagram Reel URL, not a profile or homepage.');
    if(detected==='youtube'&&!(parsed.pathname.toLowerCase().includes('/shorts/')||parsed.pathname.toLowerCase()==='/watch'||parsed.pathname.toLowerCase().includes('/embed/')||parsed.pathname.toLowerCase().includes('/live/')||parsed.searchParams.has('v')||host==='youtu.be'))throw new Error('Paste a direct YouTube video or Shorts URL.');
    const {data:existing,error:duplicateError}=await supabase.from('related_videos').select('id').eq('video_url',raw).maybeSingle();
    if(duplicateError)throw duplicateError;
    if(existing)throw new Error('This video link is already in the RykoTube catalogue.');
    const {error}=await supabase.from('related_videos').insert({title_id:videoForm.title_id||null,video_url:raw,platform:detected,video_title:videoForm.video_title.trim()||null,creator_name:videoForm.creator_name.trim()||null,creator_avatar_url:videoForm.creator_avatar_url.trim()||null,action_type:videoForm.action_type,action_url:videoForm.action_url.trim()||raw,sort_order:Number(videoForm.sort_order)||0});
    if(error)throw error;
    setVideoForm(blankVideo);await loadVideos();setNotice('Related video added to RykoTube and the public video feed.');
  }catch(e){setError(errText(e));}finally{setBusy(false);}};
  const deleteVideo=async(id:string)=>{if(!confirm('Remove this related video from the website?'))return;const {error}=await supabase.from('related_videos').delete().eq('id',id);if(error)setError(error.message);else await loadVideos();};
  const addVideoSource=async(e:FormEvent)=>{
    e.preventDefault();setDiscoveryBusy(true);setError('');
    try{
      let key=sourceForm.source_key.trim();
      let sourceType=sourceForm.source_type;
      if(key.toLowerCase().startsWith('http://') || key.toLowerCase().startsWith('https://')){
        const u=new URL(key);
        const host=u.hostname.toLowerCase().replace(/^www[.]/i,'');
        const playlistId=u.searchParams.get('list');
        const segments=u.pathname.split('/').filter(Boolean);
        // Detect playlist URLs automatically, even when the source dropdown is still set to channel.
        if(playlistId && (segments.includes('playlist') || host==='youtube.com' || host==='m.youtube.com')){
          sourceType='playlist';
          key=playlistId;
        }else if(sourceType==='channel'){
          const channelIndex=segments.indexOf('channel');
          const channelId=channelIndex>=0?segments[channelIndex+1]||'':'';
          const handle=segments.find(part=>part.startsWith('@'));
          key=channelId || (handle||'');
        }else key=playlistId||'';
      }
      const valid=sourceType==='channel'
        ? (/^[a-zA-Z0-9_-]{10,80}$/.test(key)||/^@[a-zA-Z0-9._-]{3,100}$/.test(key))
        : /^[a-zA-Z0-9_-]{10,80}$/.test(key);
      if(!valid)throw new Error(sourceType==='channel'?'Enter a YouTube channel ID or handle URL (for example @museindiachannel).':'Could not read a playlist ID. Paste the full YouTube playlist URL or its ID.');
      const {data:createdSource,error}=await supabase.from('video_discovery_sources').insert({source_type:sourceType,source_key:key,label:sourceForm.label.trim()||key}).select('*').single();
      if(error)throw error;
      setSourceForm({source_type:'channel',source_key:'',label:''});
      await loadDiscovery();
      // Adding a source now immediately starts discovery. Results remain pending
      // until the admin approves them, so nothing is published without review.
      await discoverFromSource(createdSource as VideoSourceRow);
    }catch(e){setError(errText(e));}finally{setDiscoveryBusy(false);}
  };
  const discoverFromSource=async(source:VideoSourceRow)=>{setDiscoveryBusy(true);setError('');try{const {data,error}=await supabase.functions.invoke('video-discovery',{body:{source_id:source.id}});if(error){let detail=error.message||'Edge Function request failed.';const context=(error as any).context;if(context instanceof Response){try{const payload=await context.clone().json();detail=payload?.error||detail;}catch{}}throw new Error(detail);}if(!data?.ok)throw new Error(data?.error||'Could not discover videos.');await loadDiscovery();const fetched=Number(data.fetched||0),added=Number(data.added||0),published=Number(data.already_published||0);setNotice('Checked '+source.label+': fetched '+fetched+' videos; '+added+' added to approval queue'+(published?', '+published+' already published':'')+'. Provider: '+(data.provider||'YouTube RSS')+'.');}catch(e){setError(errText(e));}finally{setDiscoveryBusy(false);}};
  const discoverAllSources=async()=>{for(const source of videoSources.filter(item=>item.enabled)){await discoverFromSource(source);}};
  const addStarterSources=async()=>{
    const starter=[{source_type:'channel',source_key:'@museindiachannel',label:'Muse India'},{source_type:'channel',source_key:'@MuseAsia',label:'Muse Asia'},{source_type:'channel',source_key:'@anioneasia',label:'Ani-One Asia'}];
    setDiscoveryBusy(true);setError('');
    try{
      const {data:existing,error:readError}=await supabase.from('video_discovery_sources').select('source_type,source_key');
      if(readError)throw readError;
      const known=new Set((existing||[]).map((item:any)=>item.source_type+':'+item.source_key.toLowerCase()));
      const missing=starter.filter(item=>!known.has(item.source_type+':'+item.source_key.toLowerCase()));
      if(missing.length){const {error:insertError}=await supabase.from('video_discovery_sources').insert(missing);if(insertError)throw insertError;}
      await loadDiscovery();
      setNotice(missing.length?'Added '+missing.length+' trusted starter channels. Run Discover all sources to collect their latest uploads.':'Your starter channels are already installed.');
    }catch(e){setError(errText(e));}
    finally{setDiscoveryBusy(false);}
  };
  const deleteVideoSource=async(id:string)=>{if(!confirm('Remove this discovery source? Existing published videos will stay.'))return;const {error}=await supabase.from('video_discovery_sources').delete().eq('id',id);if(error)setError(error.message);else await loadDiscovery();};
  const approveDiscoveredVideo=async(video:DiscoveredVideoRow)=>approveVideoBatch([video]);
  const toggleVideoSelection=(id:string,checked:boolean)=>setSelectedVideoIds(old=>checked?[...old.filter(x=>x!==id),id]:old.filter(x=>x!==id));
  const approveVideoBatch=async(items:DiscoveredVideoRow[])=>{
    if(!items.length)return;
    setDiscoveryBusy(true);setError('');
    try{
      const payload=items.map(video=>({title_id:approvalTitle||null,source_id:video.source_id||null,video_url:video.video_url,platform:'youtube',video_title:video.video_title||'Untitled YouTube video',creator_name:video.creator_name?.trim()||'YouTube',creator_avatar_url:video.creator_avatar_url||'',action_type:'watch',action_url:video.video_url,sort_order:0}));
      // A video may already have been published manually or through another source.
      // Ignore duplicates instead of failing the entire approval batch.
      const {error:publishError}=await supabase.from('related_videos').upsert(payload,{onConflict:'title_id,video_url',ignoreDuplicates:true});
      if(publishError)throw new Error('Could not publish approved videos: '+publishError.message);
      const {error:reviewError}=await supabase.from('video_discovery_queue').update({status:'approved',reviewed_at:new Date().toISOString()}).in('id',items.map(video=>video.id));
      if(reviewError)throw reviewError;
      await Promise.all([loadVideos(),loadDiscovery()]);
      setNotice(items.length===1?'Video approved and published.':items.length+' videos approved and published.');
    }catch(e){setError(errText(e));}
    finally{setDiscoveryBusy(false);}
  };
  const approveAllPending=async()=>{if(!discoveredVideos.length)return;if(!confirm('Approve and publish all '+discoveredVideos.length+' pending videos?'))return;await approveVideoBatch(discoveredVideos);};
  const approveSelected=async()=>{const chosen=discoveredVideos.filter(video=>selectedVideoIds.includes(video.id));if(!chosen.length)return;await approveVideoBatch(chosen);};
  const rejectDiscoveredVideo=async(video:DiscoveredVideoRow)=>{const {error}=await supabase.from('video_discovery_queue').update({status:'rejected',reviewed_at:new Date().toISOString()}).eq('id',video.id);if(error)setError(error.message);else{await loadDiscovery();setNotice('Video rejected; it will not appear in the public catalogue.');}};
  const moderate=async(id:string,status:'approved'|'rejected')=>{setBusy(true);try{const {error}=await supabase.from('community_comments').update({moderation_status:status}).eq('id',id);if(error)throw error;await loadModeration();setNotice('Moderation decision saved.');}catch(e){setError(errText(e));}finally{setBusy(false);}};
  const removeVideoReport=async(id:string)=>{if(!confirm('Dismiss this video report?'))return;const {error}=await supabase.from('video_reports').delete().eq('id',id);if(error)setError(error.message);else await loadReports();};
  const removeReport=async(id:string)=>{if(!confirm('Delete this report record?'))return;const {error}=await supabase.from('community_comment_reports').delete().eq('id',id);if(error)setError(error.message);else await loadReports();};
  const runAniListSync=async(reset=false)=>{
    if(aniSyncRunning)return;
    aniSyncStopRef.current=false;
    setAniSyncRunning(true);
    setError('');
    setNotice('Connecting to AniList catalogue sync…');
    let batches=0;
    try{
      let done=false;
      while(!done && !aniSyncStopRef.current && batches<8){
        const {data,error:syncError}=await supabase.functions.invoke('anilist-catalogue-sync',{body:{reset:reset&&batches===0}});
        if(syncError)throw syncError;
        if(!data?.ok)throw new Error(data?.error||'AniList catalogue sync failed.');
        setAniSyncProgress(data.state);
        done=!!data.done;
        batches+=1;
      }
      if(done){
        setNotice('AniList catalogue sync completed. All available non-adult anime and manga pages have been scanned; YouTube trailers were added to Videos.');
        await Promise.all([loadTitles(),loadVideos()]);
      }else if(aniSyncStopRef.current){
        setNotice('AniList sync paused. Your checkpoint is saved; press Continue sync to resume.');
      }else{
        setNotice('Batch checkpoint saved. Press Continue sync to import the next set of AniList pages.');
        await loadTitles();
      }
    }catch(e){
      setError(errText(e));
      setNotice('AniList sync stopped safely. Its last completed checkpoint is saved; press Continue sync after resolving the issue.');
    }finally{
      setAniSyncRunning(false);
    }
  };
  const saveSocial=async()=>{setBusy(true);try{const {error}=await supabase.from('site_settings').upsert({key:'social_links',value:socialLinks},{onConflict:'key'});if(error)throw error;setNotice('Social links saved to the live website footer.');}catch(e){setError(errText(e));}finally{setBusy(false);}};
  const addSocial=()=>setSocialLinks(old=>[...old,{platform:'Website',label:'Website',url:'https://'}]);
  const setField=(key:string,value:any)=>setTitleForm((old:any)=>({...old,[key]:value}));
  const totalBytes=storage.reduce((n,r)=>n+Number(r.bytes_used||0),0);
  const totalFiles=storage.reduce((n,r)=>n+Number(r.file_count||0),0);
  const tabs:[Tab,string,any][]=[['overview','Overview',LayoutDashboard],['catalogue','Catalogue',BookOpen],['characters','Characters',Users],['videos','Related videos',Link2],['moderation','Moderation',ShieldCheck],['reports','Reports',Flag],['settings','Website settings',Link2],['storage','Storage',HardDrive]];
  if(checking)return <main className="admin-shell"><div className="admin-loading">Verifying administrator access…</div></main>;
  if(!admin)return <main className="admin-shell"><section className="admin-denied"><ShieldCheck size={34}/><h1>Admin access required</h1><p>{error||'Sign in with an account that is listed in the website admin_users table.'}</p><Link className="admin-action" to="/account">Open account</Link><button onClick={()=>void verify()} className="admin-action">Recheck access</button></section></main>;

  return <main className="admin-shell">
    <header className="admin-hero"><div><span className="admin-kicker"><ShieldCheck size={14}/> RYKOCOLLECTION · ADMIN</span><h1>Control <em>workspace.</em></h1><p>Changes sync directly to the live RykoCollection website catalogue and community.</p></div><div className="admin-identity"><span>Signed in as</span><strong>{userEmail}</strong><button onClick={()=>void supabase.auth.signOut()}>Sign out</button></div></header>
    <div className="admin-layout"><aside className="admin-sidebar"><div className="admin-sidebar-label">WORKSPACE</div>{tabs.map(([id,label,Icon])=><button key={id} className={tab===id?'active':''} onClick={()=>setTab(id)}><Icon size={17}/>{label}</button>)}<button className="admin-refresh" onClick={()=>void refresh()} disabled={busy}><RefreshCw size={15}/> Refresh data</button></aside>
    <section className="admin-content">
      {error&&<div className="admin-alert error"><AlertCircle size={16}/>{error}</div>}{notice&&<div className="admin-alert success"><Check size={16}/>{notice}<button onClick={()=>setNotice('')}><X size={14}/></button></div>}
      {tab==='overview'&&<><div className="admin-section-head"><div><span className="admin-kicker">LIVE DATABASE</span><h2>Dashboard overview</h2><p>Current counts from the shared website database.</p></div><button className="admin-action" onClick={()=>void refresh()}><RefreshCw size={15}/> Refresh</button></div><div className="admin-stat-grid">{[['Catalogue entries',titleTotal],['Character profiles',characters.length],['Related videos',videos.length],['Pending moderation',comments.length],['Community reports',reports.length+videoReports.length],['Stored files',totalFiles]].map(([label,value])=><article className="admin-stat" key={String(label)}><span>{label}</span><strong>{Number(value).toLocaleString()}</strong></article>)}</div><div className="admin-panel"><h3>Quick actions</h3><div className="admin-quick-actions"><button onClick={()=>{setEditingTitle(null);setTitleForm(blankTitle);setTab('catalogue')}}><Plus/> Add catalogue entry</button><button onClick={()=>setTab('videos')}><Link2/> Add related video</button><button onClick={()=>setTab('moderation')}><ShieldCheck/> Review comments</button></div></div><p className="admin-footnote">Access is verified against the website’s admin_users table. Database row-level security remains responsible for protecting writes.</p></>}
      {tab==='catalogue'&&<><div className="admin-section-head"><div><span className="admin-kicker">PUBLISHING</span><h2>{editingTitle?'Edit catalogue entry':'Catalogue manager'}</h2><p>Add, update, feature or remove entries shared with the website.</p></div><button className="admin-action" onClick={()=>{setEditingTitle(null);setTitleForm(blankTitle)}}><Plus size={15}/> New entry</button></div>
      <section className="admin-panel anilist-sync-panel">
        <div className="anilist-sync-copy"><span className="admin-kicker">ANILIST CATALOGUE IMPORT</span><h3>Import the public anime & manga library</h3><p>Scans AniList's non-adult anime and manga catalogues in resumable batches. Existing AniList-linked entries are kept, and official YouTube trailers are added to the Videos section automatically.</p></div>
        {aniSyncProgress&&<div className="anilist-sync-stats"><span>Anime <strong>{Number(aniSyncProgress.animeProcessed||0).toLocaleString()} / {Number(aniSyncProgress.animeTotal||0).toLocaleString()}</strong></span><span>Manga <strong>{Number(aniSyncProgress.mangaProcessed||0).toLocaleString()} / {Number(aniSyncProgress.mangaTotal||0).toLocaleString()}</strong></span><span>New entries <strong>{Number(aniSyncProgress.titlesInserted||0).toLocaleString()}</strong></span><span>Trailer records <strong>{Number(aniSyncProgress.trailersInserted||0).toLocaleString()}</strong></span></div>}
        {aniSyncProgress&&<div className="anilist-sync-track"><span style={{width:`${Math.min(100,Math.round(((Number(aniSyncProgress.animeProcessed||0)+Number(aniSyncProgress.mangaProcessed||0))/Math.max(1,Number(aniSyncProgress.animeTotal||0)+Number(aniSyncProgress.mangaTotal||0)))*100))}%`}}/></div>}
        <div className="admin-form-actions"><button type="button" className="admin-primary" onClick={()=>void runAniListSync(!!(aniSyncProgress?.animeDone&&aniSyncProgress?.mangaDone))} disabled={aniSyncRunning}>{aniSyncRunning?<RefreshCw className="spin" size={16}/>:<Download size={16}/>} {aniSyncRunning?'Syncing AniList…':aniSyncProgress?.animeDone&&aniSyncProgress?.mangaDone?'Rescan AniList':aniSyncProgress?'Continue full sync':'Start full AniList sync'}</button>{aniSyncRunning&&<button type="button" className="admin-action" onClick={()=>{aniSyncStopRef.current=true;}}>Pause safely</button>}</div>
        <p className="admin-footnote">One run processes up to 8 batches, with 250 anime and 250 manga records per batch. Progress is saved in Supabase, so you can continue later without duplicate imports. AniList adult entries are excluded. Custom trailer URLs are never overwritten.</p>
      </section>
      <form className="admin-form" onSubmit={saveTitle}><div className="admin-form-grid">{[['name','Title name *'],['media_type','Media type'],['status','Status'],['cover_url','Cover image URL'],['banner_url','Banner image URL'],['alternative_names','Alternative names (comma separated)'],['genres','Genres (comma separated)'],['themes','Themes (comma separated)'],['release_year','Release year'],['rating','Rating (0–10)'],['chapters','Chapters'],['episodes','Episodes'],['author','Author'],['artist','Artist'],['studio','Studio'],['country','Country'],['source_url','Source URL'],['read_url','Read / Watch link'],['trailer_url','Trailer URL']].map(([key,label])=><label key={key} className={['synopsis','alternative_names','genres','themes'].includes(key)?'wide':''}>{label}{key==='media_type'?<select value={titleForm[key]} onChange={e=>setField(key,e.target.value)}>{['Manga','Manhwa','Manhua','Anime','Movie','TV Series','Light Novel','Web Novel','Video'].map(x=><option key={x}>{x}</option>)}</select>:key==='status'?<select value={titleForm[key]} onChange={e=>setField(key,e.target.value)}>{['Airing','Ongoing','Completed','Hiatus','Upcoming','Cancelled'].map(x=><option key={x}>{x}</option>)}</select>:<input required={key==='name'} type={['release_year','rating'].includes(key)?'number':'text'} value={titleForm[key]??''} onChange={e=>setField(key,e.target.value)}/>}</label>)}<label className="wide">Synopsis<textarea rows={4} value={titleForm.synopsis} onChange={e=>setField('synopsis',e.target.value)}/></label><label className="admin-check"><input type="checkbox" checked={!!titleForm.is_featured} onChange={e=>setField('is_featured',e.target.checked)}/> Featured on home</label><label className="admin-check"><input type="checkbox" checked={!!titleForm.is_trending} onChange={e=>setField('is_trending',e.target.checked)}/> Trending</label></div><div className="admin-form-actions"><button className="admin-primary" disabled={busy}><Save size={16}/>{busy?'Saving…':editingTitle?'Save changes':'Publish entry'}</button>{editingTitle&&<button type="button" className="admin-action" onClick={()=>{setEditingTitle(null);setTitleForm(blankTitle)}}>Cancel edit</button>}</div></form><div className="admin-list-head"><h3>Published entries ({titleTotal.toLocaleString()} total · showing {titles.length.toLocaleString()})</h3><label><Search size={15}/><input value={titleQuery} onChange={e=>setTitleQuery(e.target.value)} placeholder="Find a title"/></label></div><div className="admin-record-list">{titles.filter(t=>t.name.toLowerCase().includes(titleQuery.toLowerCase())).slice(0,150).map(t=><article key={t.id} className="admin-record"><img src={t.cover_url||'/placeholder-poster.svg'} alt=""/><div className="grow"><strong>{t.name}</strong><span>{t.media_type} · {t.status} · {t.release_year||'Year unknown'}</span></div><button aria-label="Edit entry" onClick={()=>editTitle(t)}><Pencil size={16}/></button><button aria-label="Delete entry" onClick={()=>void deleteTitle(t.id)}><Trash2 size={16}/></button></article>)}</div></>}
      {tab==='characters'&&<><div className="admin-section-head"><div><span className="admin-kicker">CAST DATABASE</span><h2>Character profiles</h2><p>Add and remove character profiles for shared title pages.</p></div></div><form className="admin-form" onSubmit={saveCharacter}><div className="admin-form-grid"><label>Title<select required value={characterForm.title_id} onChange={e=>setCharacterForm({...characterForm,title_id:e.target.value})}><option value="">Choose a title</option>{titles.map(t=><option value={t.id} key={t.id}>{t.name}</option>)}</select></label><label>Name<input required value={characterForm.name} onChange={e=>setCharacterForm({...characterForm,name:e.target.value})}/></label><label>Role<input value={characterForm.role} onChange={e=>setCharacterForm({...characterForm,role:e.target.value})}/></label><label>Image URL<input value={characterForm.image_url} onChange={e=>setCharacterForm({...characterForm,image_url:e.target.value})}/></label><label className="wide">Biography<textarea rows={3} value={characterForm.description} onChange={e=>setCharacterForm({...characterForm,description:e.target.value})}/></label></div><button className="admin-primary" disabled={busy}><Plus size={16}/> Add character</button></form><div className="admin-record-list">{characters.map(c=><article className="admin-record" key={c.id}><img src={c.image_url||'/placeholder-poster.svg'} alt=""/><div className="grow"><strong>{c.name}</strong><span>{titles.find(t=>t.id===c.title_id)?.name||'Unknown title'} · {c.role}</span></div><button onClick={()=>void deleteCharacter(c.id)} aria-label="Delete character"><Trash2 size={16}/></button></article>)}</div></>}
      {tab==='videos'&&<><section className="admin-panel admin-shorts-panel"><div className="admin-section-head"><div><span className="admin-kicker">SHORT-FORM VIDEO</span><h2>Add Shorts</h2><p>Publish a YouTube Short, Instagram Reel or TikTok video to the RykoTube Shorts feed.</p></div><span className="video-discovery-count">{videos.filter(v=>/youtube\.com\/shorts|instagram\.com\/(reel|reels)|tiktok\.com/i.test(v.video_url)).length} shorts</span></div><form className="admin-form" onSubmit={addShort}><div className="admin-form-grid"><label>Platform<select value={shortForm.platform} onChange={e=>setShortForm({...shortForm,platform:e.target.value as 'youtube'|'instagram'|'tiktok'})}><option value="youtube">YouTube Shorts</option><option value="instagram">Instagram Reels</option><option value="tiktok">TikTok</option></select></label><label>Video title<input value={shortForm.video_title} onChange={e=>setShortForm({...shortForm,video_title:e.target.value})} placeholder="Optional title"/></label><label className="wide">Short / Reel URL<input required type="url" value={shortForm.video_url} onChange={e=>setShortForm({...shortForm,video_url:e.target.value})} placeholder={shortForm.platform==='youtube'?'https://youtube.com/shorts/…':shortForm.platform==='instagram'?'https://www.instagram.com/reel/…':'https://www.tiktok.com/@…/video/…'}/></label><label>Creator / channel<input value={shortForm.creator_name} onChange={e=>setShortForm({...shortForm,creator_name:e.target.value})} placeholder="Optional creator name"/></label></div><button className="admin-primary" disabled={shortBusy}><Plus size={15}/> {shortBusy?'Adding…':'Add to Shorts'}</button></form></section><div className="admin-section-head"><div><span className="admin-kicker">CURATED YOUTUBE LIBRARY</span><h2>Video catalogue</h2><p>Track trusted anime distributors and studios, collect their latest uploads, then approve videos before they appear on RykoCollection.</p></div><div className="admin-form-actions"><button type="button" className="admin-primary" disabled={discoveryBusy||!videoSources.length} onClick={()=>void discoverAllSources()}><Radar size={15}/> Discover all sources</button><button type="button" className="admin-action" onClick={()=>void Promise.all([loadVideos(),loadDiscovery()]).catch(e=>setError(errText(e)))}><RefreshCw size={15}/> Refresh</button></div></div><section className="admin-panel video-discovery-panel"><div className="video-discovery-heading"><div><span className="admin-kicker"><Radar size={13}/> TRUSTED SOURCES</span><h3>Channel library</h3><p>Start with known anime distributors, then add official studios and creators you trust.</p></div><span className="video-discovery-count">{videoSources.length} sources</span></div><div className="admin-form-actions"><button type="button" className="admin-primary" disabled={discoveryBusy} onClick={()=>void addStarterSources()}><Plus size={15}/> Install starter channels</button></div><form className="admin-form video-source-form" onSubmit={addVideoSource}><div className="admin-form-grid"><label>Source type<select value={sourceForm.source_type} onChange={e=>setSourceForm({...sourceForm,source_type:e.target.value as 'channel'|'playlist'})}><option value="channel">YouTube channel</option><option value="playlist">YouTube playlist</option></select></label><label>Display name<input required value={sourceForm.label} onChange={e=>setSourceForm({...sourceForm,label:e.target.value})} placeholder="e.g. Muse India"/></label><label className="wide">Channel ID / Playlist ID or URL<input required value={sourceForm.source_key} onChange={e=>setSourceForm({...sourceForm,source_key:e.target.value})} placeholder="UC… or https://www.youtube.com/playlist?list=…"/></label></div><div className="video-source-suggestions"><span>Quick-add official sources</span><button type="button" onClick={()=>setSourceForm({source_type:"channel",source_key:"@museindiachannel",label:"Muse India"})}>Muse India</button><button type="button" onClick={()=>setSourceForm({source_type:"channel",source_key:"@MuseAsia",label:"Muse Asia"})}>Muse Asia</button><button type="button" onClick={()=>setSourceForm({source_type:"channel",source_key:"@anioneasia",label:"Ani-One Asia"})}>Ani-One Asia</button></div><button className="admin-primary" disabled={discoveryBusy}><Plus size={15}/> Add source</button></form>{(['channel','playlist'] as const).map(type=>{const grouped=videoSources.filter(source=>source.source_type===type).sort((a,b)=>a.label.localeCompare(b.label,undefined,{numeric:true,sensitivity:'base'}));const label=type==='channel'?'Channels':'Playlists';return <section className="video-source-group" key={type}><div className="video-source-group-head"><div><h4>{label}</h4><span>{grouped.length} saved {type==='channel'?'channels':'playlists'}</span></div><span className="video-source-group-icon">{type==='channel'?<Youtube size={17}/>:<ListVideo size={17}/>}</span></div>{grouped.length?grouped.map(source=><div className="video-source-row" key={source.id}><div className="video-source-icon">{type==='channel'?<Youtube size={18}/>:<ListVideo size={18}/>}</div><div className="grow"><strong>{source.label}</strong><span>{source.source_key} · {source.last_checked_at?'Checked '+new Date(source.last_checked_at).toLocaleString():'Not checked yet'}</span></div><button className="admin-action" disabled={discoveryBusy} onClick={()=>void discoverFromSource(source)}><Radar size={14}/> Discover</button><button className="admin-danger" onClick={()=>void deleteVideoSource(source.id)} aria-label={'Remove '+label.toLowerCase().slice(0,-1)}><Trash2 size={15}/></button></div>):<div className="admin-empty video-source-empty">No {label.toLowerCase()} added yet.</div>}</section>})}</section><section className="admin-panel video-review-panel"><div className="video-discovery-heading"><div><span className="admin-kicker"><CheckCircle2 size={13}/> HUMAN REVIEW</span><h3>Pending approval ({discoveredVideos.length})</h3><p>Only approved videos are published. Duplicate YouTube video IDs are ignored automatically.</p><div className="admin-form-actions"><button type="button" className="admin-action" disabled={discoveryBusy||!discoveredVideos.length} onClick={()=>setSelectedVideoIds(discoveredVideos.map(video=>video.id))}>Select all</button><button type="button" className="admin-action" disabled={discoveryBusy||!selectedVideoIds.length} onClick={()=>setSelectedVideoIds([])}>Clear selection</button><button type="button" className="admin-primary" disabled={discoveryBusy||!selectedVideoIds.length} onClick={()=>void approveSelected()}><Check size={14}/> Approve selected ({selectedVideoIds.length})</button><button type="button" className="admin-primary" disabled={discoveryBusy||!discoveredVideos.length} onClick={()=>void approveAllPending()}><CheckCircle2 size={14}/> Approve all pending ({discoveredVideos.length})</button></div></div><button className="admin-action" disabled={discoveryBusy} onClick={()=>void loadDiscovery()}><RefreshCw size={14}/> Refresh queue</button></div><label className="video-approval-title">Attach approved videos to a catalogue title<select value={approvalTitle} onChange={e=>setApprovalTitle(e.target.value)}><option value="">General video</option>{titles.map(t=><option key={t.id} value={t.id}>{t.name}</option>)}</select></label>{discoveredVideos.length?discoveredVideos.map(video=><article className="video-review-row" key={video.id}><input type="checkbox" aria-label={"Select "+video.video_title} checked={selectedVideoIds.includes(video.id)} onChange={e=>toggleVideoSelection(video.id,e.target.checked)}/>{video.thumbnail_url?<img src={video.thumbnail_url} alt="" loading="lazy"/>:<div className="video-review-placeholder"><Youtube/></div>}<div className="grow"><strong>{video.video_title}</strong><span>{video.creator_name||'YouTube creator'} · {video.category||'Uncategorized'} · {video.video_id}</span><span>{video.relevance_score!=null?'Relevance '+video.relevance_score+'/100':''}{video.view_count!=null?' · '+Number(video.view_count).toLocaleString()+' views':''}</span>{video.description&&<small>{video.description.slice(0,180)}</small>}<a href={video.video_url} target="_blank" rel="noopener noreferrer">Preview on YouTube ↗</a></div><button className="admin-primary" disabled={discoveryBusy} onClick={()=>void approveDiscoveredVideo(video)}><Check size={14}/> Approve</button><button className="admin-danger" disabled={discoveryBusy} onClick={()=>void rejectDiscoveredVideo(video)}><XCircle size={15}/></button></article>):<div className="admin-empty">No pending videos. Run discovery on one of your sources.</div>}</section><section className="admin-panel"><h3>Manual video entry</h3><p className="admin-footnote">You can still add an individual video manually below.</p><form className="admin-form" onSubmit={saveVideo}><div className="admin-form-grid"><label>Related title<select value={videoForm.title_id} onChange={e=>setVideoForm({...videoForm,title_id:e.target.value})}><option value="">General video (no title)</option>{titles.map(t=><option key={t.id} value={t.id}>{t.name}</option>)}</select></label><label>Platform<select value={videoForm.platform} onChange={e=>setVideoForm({...videoForm,platform:e.target.value})}>{['youtube','instagram','dailymotion','tiktok'].map(x=><option key={x}>{x}</option>)}</select></label><label className="wide">Video URL<input required type="url" value={videoForm.video_url} onChange={e=>setVideoForm({...videoForm,video_url:e.target.value})}/></label><label>Video title<input value={videoForm.video_title} onChange={e=>setVideoForm({...videoForm,video_title:e.target.value})}/></label><label>Creator / channel<input value={videoForm.creator_name} onChange={e=>setVideoForm({...videoForm,creator_name:e.target.value})}/></label><label>Channel avatar URL<input value={videoForm.creator_avatar_url} onChange={e=>setVideoForm({...videoForm,creator_avatar_url:e.target.value})}/></label><label>Display order<input type="number" value={videoForm.sort_order} onChange={e=>setVideoForm({...videoForm,sort_order:e.target.value})}/></label><label>Action type<select value={videoForm.action_type} onChange={e=>setVideoForm({...videoForm,action_type:e.target.value})}><option value="watch">Watch</option><option value="read">Read</option></select></label><label>Action URL<input value={videoForm.action_url} onChange={e=>setVideoForm({...videoForm,action_url:e.target.value})}/></label></div><button className="admin-primary" disabled={busy}><Plus size={16}/> Add related video</button></form></section><div className="admin-list-head"><h3>Published videos ({videos.length})</h3></div><div className="admin-record-list">{videos.map(v=><article className="admin-record" key={v.id}><div className="admin-video-icon"><Link2/></div><div className="grow"><strong>{v.video_title||v.video_url}</strong><span>{titles.find(t=>t.id===v.title_id)?.name||'General'} · {v.platform} · {v.creator_name||'Creator not set'}</span></div><button onClick={()=>void deleteVideo(v.id)} aria-label="Delete video"><Trash2 size={16}/></button></article>)}</div></>}{tab==='moderation'&&<><div className="admin-section-head"><div><span className="admin-kicker">COMMUNITY SAFETY</span><h2>Pending moderation</h2><p>Approve or reject comments awaiting review.</p></div><button className="admin-action" onClick={()=>void loadModeration()}><RefreshCw size={15}/> Refresh</button></div>{comments.length?comments.map(c=><article className="admin-panel admin-moderation-card" key={c.id}><div className="admin-record-head"><strong>{titles.find(t=>t.id===c.title_id)?.name||'Community post'}</strong><span>{new Date(c.created_at).toLocaleString()}</span></div><p>{c.body||'[Image / GIF attachment]'}</p>{c.media_url&&<img className="admin-media-preview" src={c.media_url} alt="Community attachment"/>}<div className="admin-form-actions"><button className="admin-primary" onClick={()=>void moderate(c.id,'approved')}><Check size={15}/> Approve</button><button className="admin-danger" onClick={()=>void moderate(c.id,'rejected')}><X size={15}/> Reject</button></div></article>):<div className="admin-empty">No pending comments right now.</div>}</>}
      {tab==='reports'&&<><div className="admin-section-head"><div><span className="admin-kicker">READER FEEDBACK</span><h2>Community reports</h2><p>Review user-submitted reports. Removing a report does not remove the reported comment.</p></div><button className="admin-action" onClick={()=>void loadReports()}><RefreshCw size={15}/> Refresh</button></div>{reports.length?reports.map(r=><article className="admin-panel admin-report-card" key={r.id}><div className="admin-record-head"><strong>{r.reason}</strong><span>{new Date(r.created_at).toLocaleString()}</span></div><p>{r.details||'No extra details provided.'}</p><small>Comment ID: {r.comment_id}</small><button className="admin-danger" onClick={()=>void removeReport(r.id)}><Trash2 size={14}/> Dismiss report</button></article>):<div className="admin-empty">No reports found.</div>}
        <div className="admin-section-head" style={{marginTop:28}}><div><span className="admin-kicker">VIDEO FEEDBACK</span><h2>Video reports</h2><p>Reports sent from the ••• menu on video cards.</p></div></div>
        {videoReportsMissing?<div className="admin-empty">The video_reports table is not set up yet. Run supabase/migrations/20261104000000_video_reports.sql in the Supabase SQL editor.</div>:videoReports.length?videoReports.map(r=><article className="admin-panel admin-report-card" key={r.id}><div className="admin-record-head"><strong>{r.reason}</strong><span>{new Date(r.created_at).toLocaleString()}</span></div><p>{r.details||'No extra details provided.'}</p><small>{r.video_title||'Untitled video'} · <a href={r.video_url} target="_blank" rel="noopener noreferrer">{r.video_url}</a></small><button className="admin-danger" onClick={()=>void removeVideoReport(r.id)}><Trash2 size={14}/> Dismiss report</button></article>):<div className="admin-empty">No video reports.</div>}</>}
      {tab==='settings'&&<><div className="admin-section-head"><div><span className="admin-kicker">SITE CONFIGURATION</span><h2>Website settings</h2><p>Update social and contact links used by the live website footer.</p></div><button className="admin-action" onClick={addSocial}><Plus size={15}/> Add link</button></div>{socialLinks.map((item:any,i:number)=><div className="admin-social-row" key={i}><input aria-label="Platform" value={item.platform||item.label||''} onChange={e=>setSocialLinks(old=>old.map((x,j)=>i===j?{...x,platform:e.target.value,label:e.target.value}:x))} placeholder="Platform"/><input aria-label="URL" value={item.url||''} onChange={e=>setSocialLinks(old=>old.map((x,j)=>i===j?{...x,url:e.target.value}:x))} placeholder="https://..."/><button onClick={()=>setSocialLinks(old=>old.filter((_,j)=>j!==i))} aria-label="Remove link"><Trash2 size={16}/></button></div>)}<button className="admin-primary" onClick={()=>void saveSocial()} disabled={busy}><Save size={16}/> Save live website links</button></>}
      {tab==='storage'&&<><div className="admin-section-head"><div><span className="admin-kicker">FILE USAGE</span><h2>Storage overview</h2><p>Usage from the website’s protected admin_storage_usage function.</p></div><button className="admin-action" onClick={()=>void loadStorage()}><RefreshCw size={15}/> Refresh</button></div><div className="admin-stat-grid"><article className="admin-stat"><span>Total files</span><strong>{totalFiles.toLocaleString()}</strong></article><article className="admin-stat"><span>Storage used</span><strong>{(totalBytes/1024/1024).toFixed(2)} MB</strong></article></div><div className="admin-record-list">{storage.map(r=><article className="admin-record" key={r.bucket_id}><div className="grow"><strong>{r.bucket_id}</strong><span>{Number(r.file_count||0).toLocaleString()} files</span></div><strong>{(Number(r.bytes_used||0)/1024/1024).toFixed(2)} MB</strong></article>)}</div></>}
    </section></div>
  </main>;
}
