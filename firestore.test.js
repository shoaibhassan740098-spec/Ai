const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

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

test("Unauthenticated user: cannot read chat_sessions", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("chat_sessions").doc("session_1").get());
});

test("Authenticated user: can create valid chat_session and read it", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const sessionRef = aliceDb.collection("chat_sessions").doc("session_1");

  await assertSucceeds(
    sessionRef.set({
      userId: ALICE_UID,
      title: "Urdu Assistant Chat",
      language: "ur",
      lastMessage: "ہیلو، میں کیسی مدد کر سکتا ہوں؟",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(sessionRef.get());
});

test("Authenticated user: cannot read another user's session", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("chat_sessions").doc("bob_session").set({
      userId: BOB_UID,
      title: "Bob's Chat",
      language: "en",
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("chat_sessions").doc("bob_session").get());
});

test("Authenticated user: can query own sessions with userId filter", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("chat_sessions").doc("alice_s1").set({
      userId: ALICE_UID,
      title: "Science Q&A",
      language: "en",
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("chat_sessions").where("userId", "==", ALICE_UID).get()
  );
});

test("Authenticated user: cannot query sessions without userId filter (rules are not filters)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("chat_sessions").get());
});

test("Authenticated user: cannot spoof userId on message creation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("chat_messages").doc("msg_1").set({
      sessionId: "session_1",
      userId: BOB_UID, // Spoofed!
      role: "user",
      content: "Hello",
      language: "en",
      createdAt: new Date(),
    })
  );
});
