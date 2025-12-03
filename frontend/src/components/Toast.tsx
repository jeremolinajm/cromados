// src/components/Toast.tsx
import { createContext, useContext, useState, useCallback } from "react";

type ToastMsg = { id: number; text: string };
const ToastCtx = createContext<{ show: (text: string) => void } | null>(null);

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [items, setItems] = useState<ToastMsg[]>([]);
  const show = useCallback((text: string) => {
    const id = Date.now();
    setItems((prev) => [...prev, { id, text }]);
    setTimeout(() => setItems((prev) => prev.filter(i => i.id !== id)), 3000);
  }, []);
  return (
    <ToastCtx.Provider value={{ show }}>
      {children}
      <div className="fixed bottom-4 left-1/2 -translate-x-1/2 z-50 space-y-2 w-[90%] max-w-md">
        {items.map(i => (
          <div key={i.id} className="rounded-xl bg-black/90 text-white px-4 py-3 shadow-lg">
            {i.text}
          </div>
        ))}
      </div>
    </ToastCtx.Provider>
  );
}

export function useToast() {
  const ctx = useContext(ToastCtx);
  if (!ctx) throw new Error("useToast debe usarse dentro de <ToastProvider>");
  return ctx.show;
}
