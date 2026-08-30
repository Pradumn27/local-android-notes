function run(argv) {
  if (!argv || argv.length < 1) {
    return JSON.stringify({ ok: false, error: "missing payload path" });
  }
  const app = Application.currentApplication();
  app.includeStandardAdditions = true;
  const raw = app.read(Path(argv[0]), { timeout: 600 });
  const payload = JSON.parse(raw);
  const Notes = Application("Notes");

  try {
    const result = dispatch(Notes, payload);
    return JSON.stringify({ ok: true, result: result });
  } catch (e) {
    return JSON.stringify({ ok: false, error: String(e) });
  }
}

function dispatch(Notes, payload) {
  switch (payload.op) {
    case "catalog":
      return catalog(Notes);
    case "get":
      return getNote(Notes, payload.appleId);
    case "upsert":
      return upsertNote(Notes, payload);
    case "delete":
      return deleteNote(Notes, payload.appleId);
    case "create_folder":
      return createFolder(Notes, payload);
    case "move":
      return moveNote(Notes, payload);
    case "live":
      return liveNote(Notes);
    default:
      throw "unknown op: " + payload.op;
  }
}

function catalog(Notes) {
  const folders = [];
  const notes = [];
  const accounts = Notes.accounts();
  for (let i = 0; i < accounts.length; i++) {
    const account = accounts[i];
    const accountFolders = account.folders();
    for (let j = 0; j < accountFolders.length; j++) {
      const folder = accountFolders[j];
      const folderName = folder.name();
      if (folderName === "Recently Deleted") continue;
      folders.push({
        appleId: folder.id(),
        name: folderName,
        accountName: account.name(),
        accountAppleId: account.id(),
      });
    }
  }
  const allNotes = Notes.notes();
  for (let i = 0; i < allNotes.length; i++) {
    const note = allNotes[i];
    let folderAppleId = null;
    let folderName = null;
    try {
      folderAppleId = note.container().id();
      folderName = note.container().name();
    } catch (e) {}
    if (folderName === "Recently Deleted") continue;
    notes.push({
      appleId: note.id(),
      title: note.name(),
      folderAppleId: folderAppleId,
      folderName: folderName,
      createdAt: note.creationDate().toISOString(),
      modifiedAt: note.modificationDate().toISOString(),
      passwordProtected: !!note.passwordProtected(),
    });
  }
  return { folders: folders, notes: notes };
}

function liveNote(Notes) {
  let selected = [];
  try {
    selected = Notes.selection();
  } catch (e) {
    return { active: false };
  }
  const note = firstNote(selected);
  if (!note) return { active: false };
  const snapshot = snapshotNote(note);
  snapshot.active = true;
  return snapshot;
}

function firstNote(selected) {
  if (!selected) return null;
  if (selected.length === undefined) return selected;
  for (let i = 0; i < selected.length; i++) {
    if (selected[i]) return selected[i];
  }
  return null;
}

function snapshotNote(note) {
  let folderAppleId = null;
  let folderName = null;
  try {
    folderAppleId = note.container().id();
    folderName = note.container().name();
  } catch (e) {}
  const locked = !!note.passwordProtected();
  const plaintext = locked ? "" : String(note.plaintext());
  const html = locked ? "" : String(note.body());
  return {
    appleId: note.id(),
    title: note.name(),
    folderAppleId: folderAppleId,
    folderName: folderName,
    html: html,
    plaintext: plaintext,
    createdAt: note.creationDate().toISOString(),
    modifiedAt: note.modificationDate().toISOString(),
    passwordProtected: locked,
    fingerprint: String(plaintext.length) + ":" + plaintext,
  };
}

function getNote(Notes, appleId) {
  return snapshotNote(Notes.notes.byId(appleId));
}

function upsertNote(Notes, payload) {
  const html = payload.html || "<div><br></div>";
  if (payload.appleId) {
    const note = Notes.notes.byId(payload.appleId);
    note.body = html;
    if (payload.folderAppleId) {
      tryMove(Notes, note, payload.folderAppleId);
    }
    return {
      appleId: note.id(),
      title: note.name(),
      modifiedAt: note.modificationDate().toISOString(),
    };
  }
  const folder = resolveFolder(Notes, payload.folderAppleId);
  const created = Notes.Note({ body: html });
  folder.notes.push(created);
  return {
    appleId: created.id(),
    title: created.name(),
    modifiedAt: created.modificationDate().toISOString(),
  };
}

function deleteNote(Notes, appleId) {
  const note = Notes.notes.byId(appleId);
  Notes.delete(note);
  return { appleId: appleId, deleted: true };
}

function createFolder(Notes, payload) {
  const accounts = Notes.accounts();
  let account = accounts[0];
  if (payload.accountAppleId) {
    account = Notes.accounts.byId(payload.accountAppleId);
  }
  const folder = Notes.Folder({ name: payload.name || "New Folder" });
  account.folders.push(folder);
  return { appleId: folder.id(), name: folder.name() };
}

function moveNote(Notes, payload) {
  const note = Notes.notes.byId(payload.appleId);
  tryMove(Notes, note, payload.folderAppleId);
  return { appleId: note.id(), folderAppleId: payload.folderAppleId };
}

function tryMove(Notes, note, folderAppleId) {
  if (!folderAppleId) return;
  try {
    const current = note.container().id();
    if (current === folderAppleId) return;
  } catch (e) {}
  const folder = Notes.folders.byId(folderAppleId);
  try {
    Notes.move(note, { to: folder });
  } catch (e) {
    // Some Notes builds reject move; leave the note where it is.
  }
}

function resolveFolder(Notes, folderAppleId) {
  if (folderAppleId) {
    return Notes.folders.byId(folderAppleId);
  }
  const accounts = Notes.accounts();
  for (let i = 0; i < accounts.length; i++) {
    const folders = accounts[i].folders();
    for (let j = 0; j < folders.length; j++) {
      if (folders[j].name() === "Notes") return folders[j];
    }
  }
  return Notes.folders()[0];
}
