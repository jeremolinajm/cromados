// src/components/SocialLinks.tsx
import { Instagram } from "lucide-react";

type Props = { className?: string };

export default function SocialLinks({ className }: Props) {
  return (
    <div className={`flex items-center gap-3 ${className ?? ""}`}>
      {/* Instagram */}
      <a
        href="https://www.instagram.com/peluqueria_croma2/"
        target="_blank"
        rel="noreferrer"
        className="p-2 rounded-lg bg-white/10 text-white hover:bg-white/20"
        aria-label="Instagram"
      >
        <Instagram size={20} />
      </a>

      {/* TikTok */}
      <a
        href="https://www.tiktok.com/@cromados" // <-- Usuario
        target="_blank"
        rel="noreferrer"
        className="p-2 rounded-lg bg-white/10 text-white hover:bg-white/20"
        aria-label="TikTok"
      >
        <img
          src="https://cdn.jsdelivr.net/gh/simple-icons/simple-icons/icons/tiktok.svg"
          alt="TikTok"
          className="h-5 w-5"
          style={{ filter: "invert(1)" }} // lo pone blanco sobre fondo oscuro
        />
      </a>
    </div>
  );
}
