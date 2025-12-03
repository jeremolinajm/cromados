// src/lib/api.ts
import axios from "axios";

const API_URL = import.meta.env.VITE_API_URL || "http://localhost:8080";
const API_BASE = import.meta.env.VITE_API_BASE || "/api"; // IMPORTANTE: con barra

export const api = axios.create({
  baseURL: `${API_URL}${API_BASE}`, // => http://localhost:8080/api
  timeout: 15000,
});

