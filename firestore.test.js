const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_hunter_1";
const BOB_UID = "bob_hunter_2";

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

function buildProfilePayload(uid, nowMinusMs = 2000) {
  const ts = new Date(Date.now() - nowMinusMs);
  return {
    userId: uid,
    displayName: "Sung Jinwoo",
    hunterRank: "E",
    level: 1,
    totalXp: 0,
    strStat: 10,
    endStat: 10,
    vitStat: 10,
    agiStat: 10,
    currentStreakDays: 0,
    dailyTargetVolumeKg: 3000.0,
    dailyQuestCompleted: false,
    lastQuestDate: "2026-11-21",
    createdAt: ts,
    updatedAt: ts,
  };
}

function buildRoutinePayload(uid, nowMinusMs = 2000) {
  const ts = new Date(Date.now() - nowMinusMs);
  return {
    userId: uid,
    name: "Push Dungeon A",
    splitLabel: "Petto / Spalle / Tricipiti",
    targetDifficultyRank: "C",
    exercises: [
      {
        exerciseId: "bench_press",
        name: "Panca Piana Bilanciere",
        muscleGroup: "Petto",
        targetSets: 4,
        targetReps: 8,
        suggestedWeightKg: 80.0,
        restSeconds: 120,
        notes: "Controllo 3s in discesa",
      },
    ],
    createdAt: ts,
    updatedAt: ts,
  };
}

function buildSessionPayload(uid, status = "IN_PROGRESS", nowMinusMs = 2000) {
  const ts = new Date(Date.now() - nowMinusMs);
  return {
    userId: uid,
    routineId: "routine_push_1",
    routineName: "Push Dungeon A",
    status,
    startedAtMs: Date.now() - 3600000,
    completedAtMs: status === "COMPLETED" ? Date.now() - 1000 : 0,
    totalVolumeKg: 2560.0,
    previousVolumeKg: 2400.0,
    improvementPercent: 6.67,
    xpEarned: 180,
    estimatedCalories: 310,
    feelingNote: "Ottima spinta, zero fastidi",
    healthSynced: false,
    loggedSets: [
      {
        exerciseId: "bench_press",
        exerciseName: "Panca Piana Bilanciere",
        setNumber: 1,
        targetReps: 8,
        actualReps: 8,
        weightKg: 80.0,
        rpe: 8,
        toFailure: false,
        feedbackMode: "MANUAL",
        aiTip: "Mantieni 80kg per la serie 2",
      },
    ],
    createdAt: ts,
    updatedAt: ts,
  };
}

test("Unauthenticated users cannot read or write any user profile, routine, or session", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(buildProfilePayload(ALICE_UID)));
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("routines").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("sessions").get());
});

test("Owner can create, read, and partially update HunterProfile; cross-user & ghost fields rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(buildProfilePayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());

  // Partial update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      hunterRank: "D",
      level: 4,
      totalXp: 420,
      dailyQuestCompleted: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Immutable createdAt change rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Shadow update with ghost field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Cross-user read/write rejected
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ level: 99, updatedAt: new Date(Date.now() - 100) }));
});

test("Owner can create, list, and update WorkoutRoutine; @DocumentId id injection & empty exercises rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const routinesCol = aliceDb.collection("users").doc(ALICE_UID).collection("routines");
  await assertSucceeds(routinesCol.doc("routine_push_1").set(buildRoutinePayload(ALICE_UID)));
  await assertSucceeds(routinesCol.get());

  await assertSucceeds(
    routinesCol.doc("routine_push_1").update({
      name: "Push Dungeon S",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject injecting @DocumentId 'id' key
  const badRoutine = { ...buildRoutinePayload(ALICE_UID), id: "routine_push_bad" };
  await assertFails(routinesCol.doc("routine_push_bad").set(badRoutine));

  // Reject empty exercises array
  const emptyRoutine = { ...buildRoutinePayload(ALICE_UID), exercises: [] };
  await assertFails(routinesCol.doc("routine_empty").set(emptyRoutine));

  // Reject cross-user list
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("routines").get());
});

test("WorkoutSession enforces lifecycle transition and terminal state lock after COMPLETED", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const sessionsCol = aliceDb.collection("users").doc(ALICE_UID).collection("sessions");

  await assertSucceeds(sessionsCol.doc("session_1").set(buildSessionPayload(ALICE_UID, "IN_PROGRESS")));
  await assertSucceeds(sessionsCol.get());

  // Transition IN_PROGRESS -> COMPLETED succeeds
  await assertSucceeds(
    sessionsCol.doc("session_1").update({
      status: "COMPLETED",
      completedAtMs: Date.now() - 500,
      totalVolumeKg: 2880.0,
      xpEarned: 240,
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Once COMPLETED, modifying xpEarned or totalVolumeKg is blocked
  await assertFails(
    sessionsCol.doc("session_1").update({
      xpEarned: 99999,
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Updating healthSynced flag after completion succeeds
  await assertSucceeds(
    sessionsCol.doc("session_1").update({
      healthSynced: true,
      updatedAt: new Date(Date.now() - 100),
    })
  );
});
