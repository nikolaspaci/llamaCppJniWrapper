// Seed script for the `curated_models` Firestore collection.
//
// Setup:
//   1. Firebase console -> Project settings -> Service accounts -> Generate new private key.
//   2. Save the JSON as ./service-account.json (gitignored), OR export
//      GOOGLE_APPLICATION_CREDENTIALS pointing to its absolute path.
//   3. cd scripts/seed-curated-models && npm install
//
// Run:
//   npm run seed                  # upserts every entry from ./models.js
//   npm run seed -- --dry-run     # prints the doc IDs and payloads without writing
//   npm run seed -- --delete-missing  # also deletes Firestore docs that are no longer in models.js

const path = require("path");
const fs = require("fs");
const admin = require("firebase-admin");

const COLLECTION = "curated_models";
const REQUIRED_FIELDS = [
  "hfRepoId",
  "filename",
  "displayName",
  "provider",
  "paramsBillions",
  "paramsLabel",
  "quantization",
  "fileSizeBytes",
  "tags",
  "notes",
  "hasMmproj",
  "minAppVersionCode",
];

function loadCredentials() {
  if (process.env.GOOGLE_APPLICATION_CREDENTIALS) {
    return admin.credential.applicationDefault();
  }
  const local = path.join(__dirname, "service-account.json");
  if (!fs.existsSync(local)) {
    throw new Error(
      "No credentials. Set GOOGLE_APPLICATION_CREDENTIALS or drop service-account.json next to seed.js."
    );
  }
  return admin.credential.cert(require(local));
}

function docIdFor(model) {
  // Firestore allows most chars in doc IDs except "/", "." sequences, and a few reserved patterns.
  // We slug the repoId's slash to "__" and keep the filename as-is.
  return `${model.hfRepoId.replace(/\//g, "__")}__${model.filename}`;
}

function validate(models) {
  const seen = new Set();
  for (const m of models) {
    for (const field of REQUIRED_FIELDS) {
      if (m[field] === undefined || m[field] === null) {
        throw new Error(`Missing field "${field}" in entry: ${JSON.stringify(m)}`);
      }
    }
    const id = docIdFor(m);
    if (seen.has(id)) throw new Error(`Duplicate entry for ${id}`);
    seen.add(id);
  }
}

async function main() {
  const args = new Set(process.argv.slice(2));
  const dryRun = args.has("--dry-run");
  const deleteMissing = args.has("--delete-missing");

  const models = require("./models.js");
  validate(models);

  if (dryRun) {
    for (const m of models) {
      console.log(`[DRY] ${docIdFor(m)} ->`, m);
    }
    console.log(`[DRY] Would upsert ${models.length} doc(s).`);
    return;
  }

  admin.initializeApp({ credential: loadCredentials() });
  const db = admin.firestore();

  const wantedIds = new Set();
  let written = 0;
  for (const m of models) {
    const id = docIdFor(m);
    wantedIds.add(id);
    await db
      .collection(COLLECTION)
      .doc(id)
      .set({ ...m, addedAt: admin.firestore.FieldValue.serverTimestamp() }, { merge: true });
    console.log(`upserted ${id}`);
    written += 1;
  }
  console.log(`\nUpserted ${written} doc(s).`);

  if (deleteMissing) {
    const snap = await db.collection(COLLECTION).get();
    let deleted = 0;
    for (const doc of snap.docs) {
      if (!wantedIds.has(doc.id)) {
        await doc.ref.delete();
        console.log(`deleted ${doc.id}`);
        deleted += 1;
      }
    }
    console.log(`Deleted ${deleted} stale doc(s).`);
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
