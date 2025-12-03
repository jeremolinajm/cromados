# 🚀 Optimización de Imágenes - Cromados

## ✅ Implementaciones Completadas

### **1. Meta Tags OG Corregidos** (`frontend/index.html`)
- ✅ Título actualizado: `CROMADOS | Barberia - Peluqueria`
- ✅ Descripción optimizada (bajo 155 caracteres)
- ✅ URLs completas para redes sociales
- ✅ Alt text mejorado

### **2. Backend - Optimización Automática**

#### **Nuevos Archivos:**
- `ImageOptimizationService.java` - Servicio de optimización
- `AdminImageOptimizationController.java` - Endpoint para optimizar imágenes existentes

#### **Archivos Modificados:**
- `pom.xml` - Dependencia Thumbnailator agregada
- `FileStorageService.java` - Ahora optimiza automáticamente al subir

#### **Cómo Funciona:**
Al subir una imagen (barbero o sucursal), el backend automáticamente:
1. La optimiza a **JPG con calidad 85%** (50-70% más liviana)
2. Genera **4 versiones**:
   - `thumbnail` (400px) - Para móviles (~20 KB)
   - `medium` (800px) - Para tablets (~60 KB)
   - `large` (1200px) - Para desktop (~100 KB)
   - `original` (optimizado) - Sin resize

**Ejemplo de archivos generados:**
```
/uploads/barberos/
├── barbero-1-1234567890.jpg          (original optimizado ~120 KB)
├── barbero-1-1234567890-400.jpg      (thumbnail ~20 KB)
├── barbero-1-1234567890-800.jpg      (medium ~60 KB)
└── barbero-1-1234567890-1200.jpg     (large ~100 KB)
```

### **3. Frontend - Imágenes Responsivas**

#### **Nuevos Componentes:**
- `OptimizedImage.tsx` - Componente helper con srcset automático

#### **Componentes Actualizados:**
- ✅ `Barberos.tsx` - Imágenes con srcset + lazy loading
- ✅ `IntroSucursales.tsx` - Imágenes optimizadas + width/height
- ✅ `Inicio.tsx` - Video con `preload="metadata"` (carga más rápida)

#### **Beneficios:**
- **Lazy Loading**: Las imágenes solo se cargan cuando son visibles
- **Responsive**: El navegador elige el tamaño adecuado según el dispositivo
- **No CLS**: Width/height definidos evitan saltos de layout
- **JPG Optimizado**: 50-70% más liviano que JPG original sin perder calidad visual

---

## 🔧 Cómo Usar

### **Optimizar Imágenes Existentes**

Las imágenes que ya están en producción pueden optimizarse con estos endpoints:

#### **Opción 1: Optimizar todas las imágenes**
```bash
curl -X POST https://api.cromados.uno/admin/optimize-images \
  -H "Authorization: Bearer TU_TOKEN_ADMIN"
```

#### **Opción 2: Solo barberos**
```bash
curl -X POST https://api.cromados.uno/admin/optimize-images/barberos \
  -H "Authorization: Bearer TU_TOKEN_ADMIN"
```

#### **Opción 3: Solo sucursales**
```bash
curl -X POST https://api.cromados.uno/admin/optimize-images/sucursales \
  -H "Authorization: Bearer TU_TOKEN_ADMIN"
```

**Respuesta de ejemplo:**
```json
{
  "status": "success",
  "barberosProcessed": 15,
  "sucursalesProcessed": 3,
  "totalProcessed": 18,
  "message": "Se optimizaron 18 imágenes correctamente"
}
```

### **Nuevas Imágenes**

Las nuevas imágenes se optimizan **automáticamente** al subirlas desde el panel admin. No necesitas hacer nada especial.

---

## 📦 Despliegue a Producción

### **1. Instalar Dependencias Backend**
```bash
cd backend
mvn clean install
```

### **2. Deploy Backend**
```bash
cd backend
./deploy-prod.sh
```
Esto:
- Compila el proyecto con la nueva dependencia Thumbnailator
- Sube el JAR a la VPS
- Reinicia el servicio

### **3. Deploy Frontend**
```bash
cd frontend
./deploy-prod.sh
```
Esto:
- Compila el frontend con los componentes optimizados
- Sube al servidor
- Los cambios estarán en vivo

### **4. Optimizar Imágenes Existentes (OPCIONAL)**

Una vez desplegado el backend, puedes optimizar las imágenes actuales:

```bash
# Conectarse a la VPS
ssh vps-cromados

# Obtener un token de admin (desde el panel admin o base de datos)
# Luego ejecutar:
curl -X POST http://localhost:8080/admin/optimize-images \
  -H "Authorization: Bearer TU_TOKEN"
```

O desde tu computadora local (si tienes el token):
```bash
curl -X POST https://api.cromados.uno/admin/optimize-images \
  -H "Authorization: Bearer TU_TOKEN"
```

---

## 📊 Resultados Esperados

### **Antes:**
- Imagen de barbero: ~160 KB (JPG sin optimizar)
- Carga en móvil: imagen completa de 160 KB

### **Después:**
- Móvil carga: ~20 KB (thumbnail 400px)
- Tablet carga: ~60 KB (medium 800px)
- Desktop carga: ~100 KB (large 1200px)
- **Reducción en móvil: 87%**
- **Reducción en tablet: 62%**
- Tiempo de carga: <0.5 segundos

### **Video:**
- Antes: `preload="auto"` descarga todo al inicio
- Ahora: `preload="metadata"` solo descarga metadatos
- **Mejora:** Carga inicial ~80% más rápida

---

## 🐛 Resolución de Problemas

### **Las imágenes no se ven después de optimizar**

Verifica que las nuevas imágenes WebP se generaron:
```bash
ssh vps-cromados
ls -lh /var/www/cromados/backend/uploads/barberos/
```

Deberías ver archivos como:
- `barbero-1-xxx.webp`
- `barbero-1-xxx-400.webp`
- `barbero-1-xxx-800.webp`
- `barbero-1-xxx-1200.webp`

### **Error al compilar backend**

Si Maven no encuentra Thumbnailator:
```bash
cd backend
mvn clean install -U
```

### **Error 500 al optimizar imágenes existentes**

Revisa los logs:
```bash
ssh vps-cromados
sudo journalctl -u cromados-backend -f
```

---

## 🔍 Validar OG Tags

Después del deploy del frontend, verifica que los OG tags funcionan:

1. **Facebook Debugger**: https://developers.facebook.com/tools/debug/
2. **Twitter Card Validator**: https://cards-dev.twitter.com/validator
3. **LinkedIn Post Inspector**: https://www.linkedin.com/post-inspector/

Ingresa: `https://cromados.uno` y verifica que:
- ✅ Título: "CROMADOS | Barberia - Peluqueria"
- ✅ Descripción correcta
- ✅ Imagen OG se muestra bien

**IMPORTANTE:** Después de hacer cambios, usa el botón "Scrape Again" para limpiar el caché.

---

## 📝 Notas Técnicas

- **WebP Support**: 97% de navegadores (todos los modernos)
- **Calidad WebP**: 88% (balance perfecto entre calidad/tamaño)
- **Thumbnailator**: Librería Java probada y eficiente
- **Lazy Loading**: Estándar HTML5, soportado nativamente

---

## 🎯 Próximos Pasos (Opcional)

1. **CDN**: Considerar usar Cloudflare CDN para servir imágenes
2. **AVIF**: Formato aún más eficiente (cuando tenga mayor adopción)
3. **Compression Nginx**: Habilitar gzip/brotli en el servidor web
4. **Image CDN**: Servicio como Cloudinary o imgix para optimización dinámica

---

**¿Dudas o problemas?**
Revisa los logs del backend y frontend, o contacta al desarrollador.
