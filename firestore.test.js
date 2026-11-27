const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_kiko_123";
const BOB_UID = "bob_kiko_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write any KIKO AI collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("memories").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").set({
      title: "Intrusion",
      content: "Unauthorized write",
      category: "FACT",
      pinned: false,
      source: "USER",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("User profile settings: owner CRUD succeeds, cross-user and ghost fields rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      preferredLanguage: "auto",
      aiProvider: "GEMINI",
      backendEndpoint: "",
      backendModel: "gemini-3.8-flash",
      voicePitch: 1.0,
      voiceRate: 1.0,
      continuousVoice: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredLanguage: "ur",
      continuousVoice: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Cross-user read/write blocked
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      preferredLanguage: "en",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Ghost field / privilege escalation blocked
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Memories: Kotlin-aligned payload, partial update, @DocumentId id rejection, and future timestamp rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").set({
      title: "Preferred Language",
      content: "User prefers bilingual English and Urdu responses.",
      category: "PREFERENCE",
      pinned: true,
      source: "USER",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").get()
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").update({
      pinned: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject extra "id" field when @DocumentId is used
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_2").set({
      id: "mem_2",
      title: "Bad payload",
      content: "Has id field",
      category: "FACT",
      pinned: false,
      source: "USER",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_3").set({
      title: "Future",
      content: "Clock skew attack",
      category: "FACT",
      pinned: false,
      source: "USER",
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Cross-user access denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("memories").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").delete());
});

test("Reminders, Activities, and ChatMessages enforce strict ownership and immutability", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 4000);

  // Reminder create + toggle completed
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("reminders").doc("rem_1").set({
      title: "Morning System Diagnostic",
      notes: "Run memory and battery sweep",
      dueLabel: "08:00 AM",
      priority: "HIGH",
      triggerType: "ROUTINE",
      completed: false,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("reminders").doc("rem_1").update({
      completed: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Activity create succeeds, update fails (append-only)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("activities").doc("act_1").set({
      title: "Torch Activated",
      detail: "Android Control Team enabled flashlight",
      teamId: "ANDROID_CONTROL",
      status: "COMPLETED",
      createdAt: now,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("activities").doc("act_1").update({
      status: "ALERT",
    })
  );

  // Chat message with optional codeSnippet omitted and present
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chat_messages").doc("msg_1").set({
      role: "USER",
      text: "KIKO, run diagnostics",
      teamId: "INTELLIGENCE",
      language: "en",
      provider: "GEMINI",
      createdAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chat_messages").doc("msg_2").set({
      role: "KIKO",
      text: "Diagnostics complete, sir.",
      teamId: "CERTIFICATION",
      language: "en",
      provider: "GEMINI",
      codeSnippet: "fun checkHealth() = true",
      createdAt: now,
    })
  );
});

test("DevProposals enforce user approval gate and terminal state locking", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 4000);

  // Cannot create already APPROVED proposal without user approval step
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("dev_proposals").doc("prop_bad").set({
      title: "Bypass",
      moduleName: "BypassModule.kt",
      summary: "Attempt auto approve",
      codePreview: "object Bypass",
      status: "APPROVED",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Create PENDING_APPROVAL succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("dev_proposals").doc("prop_1").set({
      title: "Add Cache Layer",
      moduleName: "KikoMemoryCache.kt",
      summary: "LRU cache bounded for 4 GB RAM devices",
      codePreview: "class KikoMemoryCache(val maxEntries: Int = 64)",
      status: "PENDING_APPROVAL",
      createdAt: now,
      updatedAt: now,
    })
  );

  // User approves proposal
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("dev_proposals").doc("prop_1").update({
      status: "APPROVED",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Terminal state lock prevents subsequent mutation
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("dev_proposals").doc("prop_1").update({
      status: "REJECTED",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});
