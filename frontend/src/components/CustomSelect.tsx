import { useState, useRef, useEffect } from "react";
import { createPortal } from "react-dom";

type Option = {
  value: string;
  label: string;
};

type Props = {
  value: string;
  onChange: (value: string) => void;
  options: Option[];
  placeholder?: string;
  className?: string;
  disabled?: boolean;
  dropup?: boolean; // Abre el dropdown hacia arriba en lugar de hacia abajo
};

export default function CustomSelect({
  value,
  onChange,
  options,
  placeholder = "Seleccionar...",
  className = "",
  disabled = false,
  dropup = false,
}: Props) {
  const [isOpen, setIsOpen] = useState(false);
  const [dropdownPosition, setDropdownPosition] = useState({ top: 0, left: 0, width: 0, shouldDropUp: false });
  const containerRef = useRef<HTMLDivElement>(null);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const isScrollingRef = useRef(false);

  const selectedOption = options.find((opt) => opt.value === value);
  const displayText = selectedOption ? selectedOption.label : placeholder;

  // Calcular posición del dropdown
  const updateDropdownPosition = () => {
    if (containerRef.current) {
      const rect = containerRef.current.getBoundingClientRect();
      const dropdownHeight = 240; // max-h-60 = 15rem = 240px
      const spaceBelow = window.innerHeight - rect.bottom;
      const spaceAbove = rect.top;

      // Determinar si debe abrirse hacia arriba automáticamente
      const shouldDropUp = dropup || (spaceBelow < dropdownHeight && spaceAbove > spaceBelow);

      setDropdownPosition({
        top: shouldDropUp ? rect.top - 8 : rect.bottom + 8,
        left: rect.left,
        width: rect.width,
        shouldDropUp,
      });
    }
  };

  useEffect(() => {
    const handleTouchMove = (event: TouchEvent) => {
      // Si el touch está dentro del dropdown, marcar que se está scrolleando
      if (dropdownRef.current && dropdownRef.current.contains(event.target as Node)) {
        isScrollingRef.current = true;
      }
    };

    const handleClickOutside = (event: MouseEvent | TouchEvent) => {
      const target = event.target as Node;

      // NO cerrar si el click/touch es dentro del dropdown
      if (dropdownRef.current && dropdownRef.current.contains(target)) {
        return;
      }

      // NO cerrar si acabamos de scrollear (touchend después de scroll)
      if (event.type === 'touchend' && isScrollingRef.current) {
        isScrollingRef.current = false;
        return;
      }

      // Cerrar si es fuera del contenedor completo
      if (containerRef.current && !containerRef.current.contains(target)) {
        setIsOpen(false);
      }
    };

    const handleScroll = (event: Event) => {
      const target = event.target;

      // ✅ NO cerrar si el scroll es dentro del propio dropdown
      if (dropdownRef.current && (target === dropdownRef.current || dropdownRef.current.contains(target as Node))) {
        return;
      }

      // ✅ NO cerrar si el scroll es dentro de la tabla horizontal
      if (target instanceof HTMLElement && target.classList.contains('overflow-x-auto')) {
        return;
      }

      // ✅ Cerrar para scroll de página o window
      if (target === document || target === document.documentElement || target === document.body || target === window) {
        setIsOpen(false);
        return;
      }

      // Para otros scrolls, cerrar el dropdown
      setIsOpen(false);
    };

    if (isOpen) {
      // Reset scroll flag cuando se abre
      isScrollingRef.current = false;

      // Calcular posición inicial
      updateDropdownPosition();

      // Actualizar posición en scroll/resize
      window.addEventListener("scroll", updateDropdownPosition, true);
      window.addEventListener("resize", updateDropdownPosition);

      // Detectar touchmove para saber si el usuario está scrolleando
      document.addEventListener("touchmove", handleTouchMove, { passive: true });
      // Usar mousedown/touchend para cerrar
      document.addEventListener("mousedown", handleClickOutside);
      document.addEventListener("touchend", handleClickOutside);
      window.addEventListener("scroll", handleScroll, true);

      return () => {
        window.removeEventListener("scroll", updateDropdownPosition, true);
        window.removeEventListener("resize", updateDropdownPosition);
        document.removeEventListener("touchmove", handleTouchMove);
        document.removeEventListener("mousedown", handleClickOutside);
        document.removeEventListener("touchend", handleClickOutside);
        window.removeEventListener("scroll", handleScroll, true);
      };
    }
  }, [isOpen, dropup]);

  const handleSelect = (optValue: string) => {
    onChange(optValue);
    setIsOpen(false);
  };

  const dropdownContent = isOpen && !disabled && (
    <div
      ref={dropdownRef}
      className="max-h-60 overflow-y-auto rounded-lg border border-slate-300 bg-white shadow-lg"
      style={{
        position: 'fixed',
        top: dropdownPosition.shouldDropUp ? dropdownPosition.top : dropdownPosition.top,
        left: dropdownPosition.left,
        width: dropdownPosition.width,
        zIndex: 99999,
        WebkitOverflowScrolling: 'touch',
        touchAction: 'pan-y',
        maxHeight: '15rem',
        transform: dropdownPosition.shouldDropUp ? 'translateY(-100%)' : 'none',
      }}
    >
      {options.map((opt) => (
        <button
          key={opt.value}
          type="button"
          onClick={() => handleSelect(opt.value)}
          className={`w-full px-3 py-2 text-left text-sm hover:bg-indigo-50 ${
            opt.value === value ? "bg-indigo-100 text-indigo-700 font-medium" : "text-slate-900"
          }`}
        >
          {opt.label}
        </button>
      ))}
      {options.length === 0 && (
        <div className="px-3 py-2 text-sm text-slate-500">Sin opciones</div>
      )}
    </div>
  );

  return (
    <>
      <div ref={containerRef} className={`relative ${className}`}>
        <button
          type="button"
          disabled={disabled}
          onClick={() => !disabled && setIsOpen(!isOpen)}
          className={`w-full rounded-lg border border-slate-300 px-3 py-2 text-left text-sm bg-white flex items-center justify-between ${
            disabled ? "opacity-50 cursor-not-allowed" : "hover:border-slate-400"
          } ${!selectedOption ? "text-slate-400" : "text-slate-900"}`}
        >
          <span className="truncate">{displayText}</span>
          <svg
            className={`w-4 h-4 ml-2 transition-transform flex-shrink-0 ${isOpen ? "rotate-180" : ""}`}
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
          </svg>
        </button>
      </div>

      {dropdownContent && createPortal(dropdownContent, document.body)}
    </>
  );
}
