// Негативні та позитивні перевірки правил `firestore.rules` (TASK-027) в емуляторі Firestore.
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { Timestamp, serverTimestamp, collection, addDoc, doc, getDoc, getDocs, setDoc, updateDoc, deleteDoc } from 'firebase/firestore';

let env;
before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-flowers',
    firestore: { rules: readFileSync('../firestore.rules', 'utf8'), host: '127.0.0.1', port: 8080 },
  });
});
after(async () => { await env.cleanup(); });

const valid = () => ({
  feature: 'plant_card', plantQuery: 'Тюльпан', responseText: 'Назва: Тюльпан\nСвітло: яскраве',
  reason: 'wrong', comment: 'Невірний полив', appVersion: '1.0.0', createdAt: serverTimestamp(),
  expireAt: Timestamp.fromMillis(Date.now() + 365 * 86400000),
});
const db = () => env.unauthenticatedContext().firestore();
const reports = () => collection(db(), 'reports');

test('валідна скарга з коментарем створюється', async () => { await assertSucceeds(addDoc(reports(), valid())); });
test('валідна скарга без коментаря створюється', async () => { const d = valid(); delete d.comment; await assertSucceeds(addDoc(reports(), d)); });
test('усі причини дозволені', async () => {
  for (const r of ['wrong', 'dangerous', 'offensive', 'other']) await assertSucceeds(addDoc(reports(), { ...valid(), reason: r }));
});
test('межі довжини: рівно на межі проходить', async () => {
  await assertSucceeds(addDoc(reports(), { ...valid(), plantQuery: 'я'.repeat(100), responseText: 'я'.repeat(4000), comment: 'я'.repeat(500), appVersion: 'v'.repeat(32) }));
});

test('зайве поле відхиляється', async () => { await assertFails(addDoc(reports(), { ...valid(), userId: 'abc' })); });
test('відсутнє обовʼязкове поле відхиляється', async () => {
  for (const k of ['feature', 'plantQuery', 'responseText', 'reason', 'appVersion', 'createdAt', 'expireAt']) {
    const d = valid(); delete d[k]; await assertFails(addDoc(reports(), d));
  }
});
test('надто довгі поля відхиляються', async () => {
  await assertFails(addDoc(reports(), { ...valid(), plantQuery: 'я'.repeat(101) }));
  await assertFails(addDoc(reports(), { ...valid(), responseText: 'я'.repeat(4001) }));
  await assertFails(addDoc(reports(), { ...valid(), comment: 'я'.repeat(501) }));
  await assertFails(addDoc(reports(), { ...valid(), appVersion: 'v'.repeat(33) }));
});
test('порожні рядки відхиляються', async () => {
  await assertFails(addDoc(reports(), { ...valid(), plantQuery: '' }));
  await assertFails(addDoc(reports(), { ...valid(), responseText: '' }));
  await assertFails(addDoc(reports(), { ...valid(), comment: '' }));
});
test('невірні типи відхиляються', async () => {
  await assertFails(addDoc(reports(), { ...valid(), plantQuery: 5 }));
  await assertFails(addDoc(reports(), { ...valid(), comment: ['x'] }));
  await assertFails(addDoc(reports(), { ...valid(), appVersion: 1 }));
});
test('невідома причина або функція відхиляються', async () => {
  await assertFails(addDoc(reports(), { ...valid(), reason: 'spam' }));
  await assertFails(addDoc(reports(), { ...valid(), feature: 'other' }));
});
test('createdAt має бути серверним часом', async () => {
  await assertFails(addDoc(reports(), { ...valid(), createdAt: new Date('2020-01-01') }));
  await assertFails(addDoc(reports(), { ...valid(), createdAt: 'now' }));
});

test('expireAt: лише приблизно +12 міс.', async () => {
  await assertFails(addDoc(reports(), { ...valid(), expireAt: Timestamp.fromMillis(Date.now() - 86400000) }));
  await assertFails(addDoc(reports(), { ...valid(), expireAt: Timestamp.fromMillis(Date.now() + 3650 * 86400000) }));
  await assertFails(addDoc(reports(), { ...valid(), expireAt: 'never' }));
  await assertSucceeds(addDoc(reports(), { ...valid(), expireAt: Timestamp.fromMillis(Date.now() + 365 * 86400000) }));
});

test('читання, список, оновлення, видалення клієнтом заборонені', async () => {
  let id;
  await env.withSecurityRulesDisabled(async (ctx) => {
    const ref = doc(ctx.firestore(), 'reports', 'seed1');
    await setDoc(ref, { ...valid(), createdAt: new Date() });
    id = ref.id;
  });
  await assertFails(getDoc(doc(db(), 'reports', id)));
  await assertFails(getDocs(reports()));
  await assertFails(updateDoc(doc(db(), 'reports', id), { reason: 'other' }));
  await assertFails(deleteDoc(doc(db(), 'reports', id)));
});
test('створення з явним ID теж підпорядковується правилам, інші колекції закриті', async () => {
  await assertSucceeds(setDoc(doc(db(), 'reports', 'client-id-1'), valid()));
  await assertFails(setDoc(doc(db(), 'reports', 'client-id-2'), { ...valid(), extra: 1 }));
  await assertFails(addDoc(collection(db(), 'other'), { a: 1 }));
  await assertFails(getDocs(collection(db(), 'other')));
});
