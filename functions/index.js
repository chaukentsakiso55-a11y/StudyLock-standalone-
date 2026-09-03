"use strict";

const {VertexAI} = require("@google-cloud/vertexai");
const {logger} = require("firebase-functions");
const {HttpsError, onCall} = require("firebase-functions/v2/https");

const REGION = "us-central1";
const MODEL = "gemini-2.5-flash";
const MAX_PROMPT_CHARS = 12000;
const SYSTEM_PROMPT = [
  "You are StudyLock Tutor, a clear, encouraging academic tutor for secondary-school students.",
  "Explain concepts step by step, adapt to the student's level, and prefer hints before final answers.",
  "For quizzes, do not reveal an answer that the student is currently meant to solve unless they ask for an explanation after attempting it.",
  "Be concise, accurate, age-appropriate, and admit uncertainty instead of inventing facts."
].join(" ");

function cleanText(value, limit) {
  return typeof value === "string" ? value.trim().slice(0, limit) : "";
}

function projectId() {
  return process.env.GCLOUD_PROJECT || process.env.GOOGLE_CLOUD_PROJECT || "studylock-family";
}

exports.studyLockTutor = onCall({
  region: REGION,
  enforceAppCheck: true,
  timeoutSeconds: 60,
  memory: "512MiB",
  minInstances: 0,
  maxInstances: 5
}, async (request) => {
  const prompt = cleanText(request.data?.prompt, MAX_PROMPT_CHARS);
  const appContext = cleanText(request.data?.system, 4000);
  const requestedTokens = Number(request.data?.maxTokens);
  const maxOutputTokens = Number.isFinite(requestedTokens)
    ? Math.min(800, Math.max(64, Math.trunc(requestedTokens)))
    : 500;

  if (!prompt) {
    throw new HttpsError("invalid-argument", "A tutor question is required.");
  }

  try {
    const vertexAI = new VertexAI({project: projectId(), location: REGION});
    const model = vertexAI.getGenerativeModel({
      model: MODEL,
      systemInstruction: {
        role: "system",
        parts: [{text: appContext ? `${SYSTEM_PROMPT}\n\nApp context:\n${appContext}` : SYSTEM_PROMPT}]
      },
      generationConfig: {
        temperature: 0.35,
        maxOutputTokens
      }
    });
    const result = await model.generateContent({
      contents: [{role: "user", parts: [{text: prompt}]}]
    });
    const parts = result.response?.candidates?.[0]?.content?.parts || [];
    const text = parts.map((part) => part.text || "").join("").trim();
    if (!text) {
      throw new HttpsError("unavailable", "The tutor returned an empty answer.");
    }
    return {text, model: MODEL};
  } catch (error) {
    if (error instanceof HttpsError) throw error;
    const message = String(error?.message || error || "Unknown Vertex AI error");
    logger.error("StudyLock tutor backend failed", {
      code: error?.code || "unknown",
      message: message.slice(0, 300)
    });
    if (/billing|payment|permission|precondition|403/i.test(message)) {
      throw new HttpsError(
        "failed-precondition",
        "StudyLock AI needs Vertex AI enabled and billing active on the Firebase project."
      );
    }
    if (/quota|resource.exhausted|429/i.test(message)) {
      throw new HttpsError("resource-exhausted", "StudyLock AI has reached its current quota.");
    }
    throw new HttpsError("unavailable", "StudyLock Tutor is temporarily unavailable.");
  }
});
