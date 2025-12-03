// src/pages/Privacidad.tsx
export default function Privacidad() {
  return (
    <div className="min-h-screen bg-gray-50 py-12 px-4 sm:px-6 lg:px-8">
      <div className="max-w-3xl mx-auto bg-white rounded-lg shadow-md p-8">
        <h1 className="text-3xl font-bold text-gray-900 mb-8">
          Política de Privacidad
        </h1>

        <p className="text-sm text-gray-600 mb-8">
          Última actualización: 1 de noviembre de 2025
        </p>

        <section className="mb-8">
          <h2 className="text-2xl font-semibold text-gray-800 mb-4">
            1. Información que Recopilamos
          </h2>
          <p className="text-gray-700 mb-4">
            En Cromados, recopilamos la siguiente información cuando reservás un turno:
          </p>
          <ul className="list-disc pl-6 text-gray-700 space-y-2">
            <li><strong>Nombre y apellido</strong>: Para identificar tu reserva</li>
            <li><strong>Número de teléfono</strong>: Para enviarte confirmaciones y recordatorios por WhatsApp</li>
            <li><strong>Edad</strong>: Para cumplir con requisitos regulatorios</li>
            <li><strong>Información de la reserva</strong>: Fecha, hora, servicio seleccionado, barbero y sucursal</li>
            <li><strong>Información de pago</strong>: Procesada de forma segura a través de Mercado Pago (no almacenamos datos de tarjetas)</li>
          </ul>
        </section>

        <section className="mb-8">
          <h2 className="text-2xl font-semibold text-gray-800 mb-4">
            2. Cómo Usamos tu Información
          </h2>
          <p className="text-gray-700 mb-4">
            Utilizamos tu información personal para:
          </p>
          <ul className="list-disc pl-6 text-gray-700 space-y-2">
            <li>Procesar y confirmar tus reservas de turnos</li>
            <li>Enviarte confirmaciones y recordatorios por WhatsApp</li>
            <li>Notificar a nuestros barberos sobre nuevas reservas</li>
            <li>Procesar pagos de manera segura a través de Mercado Pago</li>
            <li>Mejorar nuestros servicios y experiencia del cliente</li>
            <li>Cumplir con requisitos legales y regulatorios</li>
          </ul>
        </section>

        <section className="mb-8">
          <h2 className="text-2xl font-semibold text-gray-800 mb-4">
            3. Comunicaciones por WhatsApp
          </h2>
          <p className="text-gray-700 mb-4">
            Al proporcionar tu número de teléfono y completar una reserva, aceptás recibir:
          </p>
          <ul className="list-disc pl-6 text-gray-700 space-y-2">
            <li>Confirmación de tu turno por WhatsApp</li>
            <li>Recordatorios de tu próximo turno (enviados 8 horas antes)</li>
            <li>Notificaciones sobre cambios o cancelaciones (si aplicable)</li>
          </ul>
          <p className="text-gray-700 mt-4">
            Utilizamos WhatsApp Business API de Meta para estas comunicaciones.
            No compartimos tu número con terceros ni enviamos publicidad no solicitada.
          </p>
        </section>

        <section className="mb-8">
          <h2 className="text-2xl font-semibold text-gray-800 mb-4">
            4. Compartir Información con Terceros
          </h2>
          <p className="text-gray-700 mb-4">
            Compartimos tu información únicamente con:
          </p>
          <ul className="list-disc pl-6 text-gray-700 space-y-2">
            <li>
              <strong>Mercado Pago</strong>: Para procesar pagos de forma segura.
              Consultá la <a href="https://www.mercadopago.com.ar/privacidad" target="_blank" rel="noopener noreferrer" className="text-blue-600 hover:underline">
                política de privacidad de Mercado Pago
              </a>.
            </li>
            <li>
              <strong>Meta (WhatsApp)</strong>: Para enviar mensajes de confirmación y recordatorios.
              Consultá la <a href="https://www.whatsapp.com/legal/privacy-policy" target="_blank" rel="noopener noreferrer" className="text-blue-600 hover:underline">
                política de privacidad de WhatsApp
              </a>.
            </li>
          </ul>
          <p className="text-gray-700 mt-4">
            <strong>No vendemos ni alquilamos</strong> tu información personal a terceros.
          </p>
        </section>

        <section className="mb-8">
          <h2 className="text-2xl font-semibold text-gray-800 mb-4">
            5. Seguridad de la Información
          </h2>
          <p className="text-gray-700">
            Implementamos medidas de seguridad técnicas y organizativas para proteger
            tu información personal contra acceso no autorizado, pérdida o alteración.
            Sin embargo, ningún método de transmisión por Internet es 100% seguro.
          </p>
        </section>

        <section className="mb-8">
          <h2 className="text-2xl font-semibold text-gray-800 mb-4">
            6. Retención de Datos
          </h2>
          <p className="text-gray-700">
            Conservamos tu información personal durante el tiempo necesario para
            cumplir con los propósitos descritos en esta política, incluyendo
            requisitos legales, contables o de informes.
          </p>
        </section>

        <section className="mb-8">
          <h2 className="text-2xl font-semibold text-gray-800 mb-4">
            7. Tus Derechos
          </h2>
          <p className="text-gray-700 mb-4">
            Según la legislación argentina de protección de datos personales, tenés derecho a:
          </p>
          <ul className="list-disc pl-6 text-gray-700 space-y-2">
            <li>Acceder a tus datos personales</li>
            <li>Rectificar datos inexactos o incompletos</li>
            <li>Solicitar la eliminación de tus datos</li>
            <li>Oponerte al procesamiento de tus datos</li>
            <li>Retirar tu consentimiento en cualquier momento</li>
          </ul>
          <p className="text-gray-700 mt-4">
            Para ejercer estos derechos, contactanos a través de nuestros canales oficiales.
          </p>
        </section>

        <section className="mb-8">
          <h2 className="text-2xl font-semibold text-gray-800 mb-4">
            8. Cookies y Tecnologías Similares
          </h2>
          <p className="text-gray-700">
            Utilizamos cookies esenciales para el funcionamiento del sitio web
            (autenticación, carrito de compra). No utilizamos cookies de seguimiento
            ni publicidad de terceros.
          </p>
        </section>

        <section className="mb-8">
          <h2 className="text-2xl font-semibold text-gray-800 mb-4">
            9. Menores de Edad
          </h2>
          <p className="text-gray-700">
            Nuestros servicios están dirigidos a personas mayores de 4 años.
            Si sos menor de 18 años, necesitás el consentimiento de un padre o tutor
            para utilizar nuestros servicios.
          </p>
        </section>

        <section className="mb-8">
          <h2 className="text-2xl font-semibold text-gray-800 mb-4">
            10. Cambios a esta Política
          </h2>
          <p className="text-gray-700">
            Podemos actualizar esta política de privacidad ocasionalmente.
            Te notificaremos sobre cambios significativos publicando la nueva política
            en esta página con una fecha de "última actualización" revisada.
          </p>
        </section>

        <section className="mb-8">
          <h2 className="text-2xl font-semibold text-gray-800 mb-4">
            11. Contacto
          </h2>
          <p className="text-gray-700 mb-4">
            Si tenés preguntas sobre esta política de privacidad o sobre cómo
            manejamos tu información personal, podés contactarnos:
          </p>
          <ul className="list-none text-gray-700 space-y-2">
            <li><strong>Sitio web</strong>: <a href="https://cromados.uno" className="text-blue-600 hover:underline">cromados.uno</a></li>
            <li><strong>WhatsApp</strong>: +54 9 3547 509878 / +54 9 3547 468607</li>
          </ul>
        </section>

        <section className="mb-8">
          <h2 className="text-2xl font-semibold text-gray-800 mb-4">
            12. Legislación Aplicable
          </h2>
          <p className="text-gray-700">
            Esta política de privacidad se rige por la{" "}
            <strong>Ley de Protección de Datos Personales N° 25.326</strong> de Argentina
            y normativas complementarias.
          </p>
        </section>

        <div className="mt-12 pt-8 border-t border-gray-200">
          <p className="text-center text-gray-600">
            Cromados - Barbería
          </p>
          <p className="text-center text-sm text-gray-500 mt-2">
            Villa María, Córdoba, Argentina
          </p>
        </div>
      </div>
    </div>
  );
}
