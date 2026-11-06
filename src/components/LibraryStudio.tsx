import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { BookmarkPlus, FolderPlus, Star, Trash2 } from 'lucide-react';

type SavedTitle = { id: string; title?: { english?: string; romaji?: string }; image?: string; coverImage?: { large?: string }; genres?: string[] };
type LibraryNote = { rating: number; note: string; collectionIds: string[] };
type Collection = { id: string; name: string; created: number };

const read = <T,>(key: string, fallback: T): T => { try { const raw=localStorage.getItem(key); return raw ? JSON.parse(raw) as T : fallback; } catch { return fallback; } };
const write = (key: string, value: unknown) => { try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* storage unavailable */ } };
const titleName = (item: SavedTitle) => item.title?.english || item.title?.romaji || 'Untitled';

export function LibraryStudio() {
  const [titles] = useState<SavedTitle[]>(() => read('kairo-watchlist', []));
  const [collections, setCollections] = useState<Collection[]>(() => read('rykoc-custom-collections', []));
  const [notes, setNotes] = useState<Record<string, LibraryNote>>(() => read('rykoc-library-notes', {}));
  const [name, setName] = useState('');
  const [selected, setSelected] = useState(titles[0]?.id || '');
  const [draft, setDraft] = useState('');
  const [rating, setRating] = useState(0);
  const [message, setMessage] = useState('');
  const active = titles.find(item => item.id === selected);
  const current = notes[selected] || { rating: 0, note: '', collectionIds: [] };

  const createCollection = () => {
    const clean = name.trim().slice(0,48);
    if (!clean) return;
    const next = [...collections, { id: crypto.randomUUID(), name: clean, created: Date.now() }];
    setCollections(next); write('rykoc-custom-collections', next); setName('');
  };
  const saveNote = () => {
    if (!selected) return;
    const next = { ...notes, [selected]: { rating, note: draft.slice(0,2000), collectionIds: current.collectionIds } };
    setNotes(next); write('rykoc-library-notes', next); setMessage('Saved privately on this device.');
  };
  const toggleCollection = (id: string) => {
    if (!selected) return;
    const collectionIds = current.collectionIds.includes(id) ? current.collectionIds.filter(x=>x!==id) : [...current.collectionIds,id];
    const next = { ...notes, [selected]: { ...current, collectionIds } };
    setNotes(next); write('rykoc-library-notes', next);
  };
  const removeCollection = (id: string) => {
    const nextCollections = collections.filter(item=>item.id!==id);
    const nextNotes = Object.fromEntries(Object.entries(notes).map(([key,value])=>[key,{...value,collectionIds:value.collectionIds.filter(x=>x!==id)}]));
    setCollections(nextCollections); setNotes(nextNotes); write('rykoc-custom-collections',nextCollections); write('rykoc-library-notes',nextNotes);
  };
  const grouped = useMemo(() => collections.map(collection => ({ ...collection, items: titles.filter(item => notes[item.id]?.collectionIds.includes(collection.id)) })), [collections,titles,notes]);

  return <main className="content-page library-studio-page">
    <div className="page-heading"><span className="eyebrow">YOUR PRIVATE LIBRARY</span><h1>Library <em>studio.</em></h1><p>Build custom collections and keep personal ratings and notes on this device only.</p></div>
    <section className="settings-card"><div className="section-head"><div><span className="eyebrow">CUSTOM COLLECTIONS</span><h2><FolderPlus size={18}/> Your collections</h2></div></div>
      <form className="collection-create" onSubmit={e=>{e.preventDefault();createCollection();}}><input value={name} onChange={e=>setName(e.target.value)} placeholder="e.g. Favourite stories" maxLength={48}/><button className="primary-btn" disabled={!name.trim()}><FolderPlus size={15}/> Create</button></form>
      {grouped.length ? <div className="custom-collection-list">{grouped.map(collection=><article className="custom-collection" key={collection.id}><div className="section-head"><div><h3>{collection.name}</h3><small>{collection.items.length} saved titles</small></div><button className="icon-btn" onClick={()=>removeCollection(collection.id)} aria-label={'Delete '+collection.name}><Trash2 size={16}/></button></div><div className="custom-collection-items">{collection.items.map(item=><Link to={'/anime/'+item.id} key={item.id}>{titleName(item)}</Link>)}</div></article>)}</div> : <p className="fine-print">Create your first collection to organise saved titles.</p>}
    </section>
    <section className="settings-card"><div className="section-head"><div><span className="eyebrow">PERSONAL NOTES & RATINGS</span><h2><Star size={18}/> Title journal</h2></div></div>
      {titles.length ? <>
        <label className="community-field">Choose a saved title<select value={selected} onChange={e=>{const id=e.target.value;setSelected(id);setDraft(notes[id]?.note||'');setRating(notes[id]?.rating||0);}}>{titles.map(item=><option key={item.id} value={item.id}>{titleName(item)}</option>)}</select></label>
        {active && <div className="library-note-editor"><h3>{titleName(active)}</h3><label>Your rating</label><div className="personal-rating">{[1,2,3,4,5,6,7,8,9,10].map(n=><button type="button" key={n} className={rating>=n?'selected':''} onClick={()=>setRating(n)} aria-label={'Rate '+n+' out of 10'}>{n}</button>)}</div><label>Private note<textarea value={draft} onChange={e=>setDraft(e.target.value)} maxLength={2000} placeholder="Keep your thoughts, favourite moments or reading notes…"/></label><small>{draft.length}/2000 characters</small><div className="collection-membership"><strong>Add to collections</strong>{collections.map(collection=><label key={collection.id}><input type="checkbox" checked={current.collectionIds.includes(collection.id)} onChange={()=>toggleCollection(collection.id)}/>{collection.name}</label>)}</div><button className="primary-btn" onClick={saveNote}><BookmarkPlus size={15}/> Save private note</button>{message && <p className="fine-print">{message}</p>}</div>}
      </> : <p className="fine-print">Save titles to your Library first, then you can add private notes and ratings.</p>}
    </section>
  </main>;
}
