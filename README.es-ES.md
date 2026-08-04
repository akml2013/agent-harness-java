
<p align="center">
  <h1 align="center">🤖 Office Work Agent Harness</h1>
  <p align="center">
    <em>Asistente de Oficina Inteligente Empresarial con Arquitectura de Ingeniería Harness</em>
  </p>
  <p align="center">
    <a href="https://www.java.com/en/download/help/whatis_java.html">
      <img alt="Java 17" src="https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white">
    </a>
    <a href="https://spring.io/projects/spring-boot">
      <img alt="Spring Boot" src="https://img.shields.io/badge/Spring_Boot-3.2-6DB33F?logo=springboot&logoColor=white">
    </a>
    <a href="https://vuejs.org/">
      <img alt="Vue 3" src="https://img.shields.io/badge/Vue-3-4FC08D?logo=vuedotjs&logoColor=white">
    </a>
    <a href="https://element-plus.org/">
      <img alt="Element Plus" src="https://img.shields.io/badge/Element_Plus-2.6-409EFF?logo=elementplus&logoColor=white">
    </a>
    <a href="https://www.docker.com/">
      <img alt="Docker" src="https://img.shields.io/badge/Docker-Ready-2496ED?logo=docker&logoColor=white">
    </a>
  </p>
  <p align="center">
    <a href="https://milvus.io/">
      <img alt="Milvus" src="https://img.shields.io/badge/Milvus-2.4-00A1EA?logo=milvus&logoColor=white">
    </a>
    <a href="https://www.elastic.co/elasticsearch">
      <img alt="Elasticsearch" src="https://img.shields.io/badge/Elasticsearch-8.12-005571?logo=elasticsearch&logoColor=white">
    </a>
    <a href="https://min.io/">
      <img alt="MinIO" src="https://img.shields.io/badge/MinIO-Object_Storage-C72E49?logo=minio&logoColor=white">
    </a>
    <a href="https://rocketmq.apache.org/">
      <img alt="RocketMQ" src="https://img.shields.io/badge/RocketMQ-5.3-D77310?logo=apacherocketmq&logoColor=white">
    </a>
    <a href="https://redis.io/">
      <img alt="Redis" src="https://img.shields.io/badge/Redis-7.2-DC382D?logo=redis&logoColor=white">
    </a>
  </p>
</p>

<p align="center">
  <img src="assets/agent-service.gif" alt="AI Agent Service Demo" width="800">
</p>

## ✨ Características

### 🧠 Motor de Razonamiento ReAct

- **Bucle Pensamiento-Acción-Observación** — el agente razona paso a paso, invoca herramientas y observa los resultados de forma iterativa
- **Una acción por ronda** — cada ronda ReAct invoca una herramienta; las herramientas de documentos agrupan múltiples operaciones mediante la matriz `actions`
- **Gestión de listas de tareas** — descompone automáticamente tareas complejas y sigue el progreso
- **Motor de flujos de trabajo** — define y ejecuta flujos de trabajo automatizados de múltiples pasos con gráfico de nodos DAG
- **Tareas programadas** — crea tareas recurrentes o únicas con reglas de repetición flexibles (ONCE/HOURLY/DAILY/WEEKLY/MONTHLY/YEARLY)
- **Sistema de habilidades** — carga documentos de habilidades específicos del dominio (Word/Excel) para guiar la generación de documentos con formato profesional

### 💾 Sistema de Memoria en Tres Capas

- **Memoria Procedimental** — almacena los patrones de comportamiento y el conocimiento operativo del Agente, incluyendo indicaciones del sistema, documentos de habilidades, definiciones de flujos de trabajo, reglas de ejecución de tareas y conciencia del tiempo actual. Esta capa moldea _cómo_ piensa y actúa el Agente
- **Memoria a Corto Plazo** — mantiene el historial de conversaciones recientes con el mecanismo **AOF (Archivo de Solo Adición) de Reescritura**: los registros de conversación sin procesar (interacciones del usuario, pensamientos de la IA, acciones/resultados de herramientas) se reorganizan por recuperación; fusionando pares de acción-resultado por ronda, eliminando duplicados en operaciones de archivos (manteniendo solo la última escritura por archivo) y truncando a un límite de rondas configurable, garantizando un contexto compacto y relevante sin ruido redundante
- **Memoria a Largo Plazo** — preserva el conocimiento persistente a través de dos estructuras complementarias:
  - **Conversaciones Efectivas** — los últimos N segmentos de diálogo de alta calidad (solicitudes del usuario y respuestas de la IA) que permanecen directamente útiles para el contexto en curso
  - **Resúmenes Históricos** — conversaciones anteriores comprimidas mediante resumen de LLM de modo dual: la _compresión incremental_ agrega nuevos fragmentos de resumen a medida que las conversaciones crecen, mientras que la _compresión completa_ reescribe todo el resumen cuando la cantidad de tokens supera el umbral, asegurando que el resumen permanezca coherente y limitado

### 🔧 Sistema de Herramientas MCP

| Herramienta        | Descripción                                                                                                                                         |
| ------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Word**           | Crear/modificar documentos Word — párrafos, tablas, estilos, encabezados/pies de página, imágenes, marcas de agua, índice y más de 39 operaciones mediante la matriz `actions`         |
| **Excel**          | Crear/modificar hojas de cálculo Excel — celdas, fórmulas, estilos, gráficos, validación de datos, formato condicional y más de 30 operaciones mediante la matriz `actions` |
| **PDF Read**       | Extraer texto y tablas de archivos PDF                                                                                                              |
| **Chart**          | Generar gráficos de visualización de datos (de barras, líneas, circular, dispersión, área)                                                                                  |
| **Knowledge Base** | Cargar y consultar documentos con búsqueda de similitud vectorial (Milvus)                                                                                   |

### 📡 Comunicación en Tiempo Real

- **SSE (Server-Sent Events)** — transmisión en tiempo real de pensamientos de la IA, llamadas a herramientas y resultados al frontend
- **Visualización de flujos de trabajo** — actualizaciones en vivo del estado de los nodos durante la ejecución del flujo de trabajo
- **Progreso de tareas** — actualizaciones en tiempo real de la lista de tareas a medida que el agente trabaja

## 🏗️ Arquitectura

```
┌─────────────┐     SSE      ┌──────────────────┐
│   Vue 3 UI  │◄────────────►│   Spring Boot    │
│  Element+   │   REST/SSE   │   Agent Core     │
└─────────────┘              │                  │
                             │  ┌────────────┐  │
                             │  │ ReAct Loop │  │
                             │  │ Think→Act  │  │
                             │  │ →Observe   │  │
                             │  └─────┬──────┘  │
                             │        │         │
                             │  ┌─────▼──────┐  │
                             │  │ MCP Tools  │  │
                             │  │ System Tool│  │
                             │  └─────┬──────┘  │
                             │        │         │
                             │  ┌─────▼──────┐  │
                             │  │  Memory    │  │
                             │  │ P/S/L Layer│  │
                             │  └────────────┘  │
                             └────────┬─────────┘
                                      │
                    ┌─────────────────┼─────────────────┐
                    │                 │                   │
              ┌─────▼────┐    ┌──────▼─────┐    ┌──────▼─────┐
              │  MySQL   │    │   Milvus   │    │   MinIO    │
              │  Redis   │    │    ES      │    │ RocketMQ   │
              └──────────┘    └────────────┘    └────────────┘
```

## 🚀 Inicio Rápido

### Docker Compose (Recomendado)

```bash
# Clone the repository
git clone https://github.com/your-username/agent-service.git
cd agent-service

# Configure environment variables
cd docker
cp .env.example .env
# Edit .env and fill in your API keys

# Start all services
docker compose up -d

# Access the application
# Frontend: http://localhost:8080
# Backend API: http://localhost:8081
```

<details>
<summary>🐳 Solución de problemas: Fallos al descargar imágenes</summary>

Si encuentra fallos al descargar imágenes debido a problemas de red, utilice el script proporcionado para descargarlas manualmente:

```bash
cd docker
powershell -ExecutionPolicy Bypass -File pull-images.ps1
```

**O descargue las imágenes manualmente:**

```bash
# Configure Docker mirror (Docker Desktop -> Settings -> Docker Engine)
{
  "registry-mirrors": [
    "https://docker.mirrors.ustc.edu.cn",
    "https://hub-mirror.c.163.com",
    "https://mirror.baidubce.com"
  ]
}
```

</details>

### Desarrollo Local

**Requisitos previos:**

- JDK 17+
- Node.js 20+
- MySQL 8.0, Redis 7, Elasticsearch 8, Milvus 2.4, MinIO, RocketMQ 5

**Backend:**

```bash
# Initialize database
mysql -u root -p < sql/agent_service.sql

# Configure application.properties (copy from example)
cp agent-core/src/main/resources/application.properties.example \
   agent-core/src/main/resources/application.properties

# Build and run
mvn clean package -DskipTests
java -jar agent-core/target/agent-core-1.0.0-SNAPSHOT.jar
```

**Frontend:**

```bash
cd agent-ui
npm install
npm run dev
# Access at http://localhost:3000
```

## ⚙️ Configuración

Variables de entorno clave para la implementación con Docker:

| Variable            | Descripción               | Predeterminado                                    |
| ------------------- | ------------------------- | ------------------------------------------ |
| `DEEPSEEK_API_KEY`  | Clave API de DeepSeek LLM      | `your-api-key`                             |
| `DEEPSEEK_BASE_URL` | URL base de la API del LLM          | `https://api.deepseek.com`                 |
| `DEEPSEEK_MODEL`    | Nombre del modelo LLM            | `deepseek-chat`                            |
| `EMBEDDING_API_KEY` | Clave API del servicio de incrustación (embedding) | `your-api-key`                             |
| `EMBEDDING_API_URL` | URL del servicio de incrustación (embedding)     | `https://api.siliconflow.cn/v1/embeddings` |
| `EMBEDDING_MODEL`   | Nombre del modelo de incrustación (embedding)      | `Qwen/Qwen3-Embedding-4B`                  |

Para el desarrollo local, edite `agent-core/src/main/resources/application.properties`.

## 📁 Estructura del Proyecto

```
agent-service/
├── agent-core/            # Spring Boot backend — ReAct engine, MCP tools, memory layer
├── agent-ui/              # Vue 3 frontend — chat UI, workflow editor, file manager
├── agent-common/          # Shared modules (common-core, common-api, common-util)
├── skills/                # AI skill documents (Word/Excel domain knowledge)
├── sql/                   # Database initialization scripts
├── docker/                # Docker Compose & Dockerfiles
│   ├── docker-compose.yml
│   ├── agent-core/
│   │   ├── Dockerfile
│   │   └── application-docker.properties
│   ├── agent-ui/
│   │   ├── Dockerfile
│   │   └── nginx.conf
│   └── rocketmq/
│       └── broker.conf
├── assets/                # Demo images and GIFs
└── docs/                  # Documentation
```

## 🛠️ Stack Tecnológico

**Backend:**

- [Spring Boot 3.2](https://spring.io/projects/spring-boot) — Marco de trabajo de la aplicación
- [MyBatis-Plus](https://baomidou.com/) — Marco de trabajo ORM
- [Apache POI](https://poi.apache.org/) — Procesamiento de documentos Word/Excel
- [Spring AI](https://spring.io/projects/spring-ai) — Marco de integración de IA
- [Milvus](https://milvus.io/) — Base de datos vectorial para búsqueda semántica
- [Elasticsearch](https://www.elastic.co/) — Búsqueda de texto completo y almacenamiento vectorial
- [MinIO](https://min.io/) — Almacenamiento de objetos para gestión de archivos
- [RocketMQ](https://rocketmq.apache.org/) — Cola de mensajes para tareas asincrónicas
- [Redis](https://redis.io/) — Caché y almacenamiento de sesiones

**Frontend:**

- [Vue 3](https://vuejs.org/) — Marco de trabajo JavaScript progresivo
- [Element Plus](https://element-plus.org/) — Biblioteca de componentes de IU
- [Vue Flow](https://vue-flow.dev/) — Visualización de DAG de flujos de trabajo
- [Vite](https://vitejs.dev/) — Herramienta de compilación
- [Pinia](https://pinia.vuejs.org/) — Gestión de estado

## 📍 Hoja de Ruta

### v1.0.0 (Actual)

- Motor de razonamiento ReAct con bucle Pensamiento-Acción-Observación
- Sistema de memoria en tres capas (Procedimental / A Corto Plazo AOF / Resumido a Largo Plazo)
- Herramientas MCP: Word (más de 39 acciones), Excel (más de 30 acciones), Lectura de PDF, Chart, Base de Conocimientos
- Herramientas del sistema: gestión de archivos, CRUD/ejecución de flujos de trabajo, tareas programadas, `ask_user`, consulta de base de conocimientos
- Comunicación SSE en tiempo real con visualización de flujos de trabajo
- Sistema de habilidades para orientación en la generación de documentos específicos del dominio

### v1.1.0 (Planeado)

- **Caché de Redis** — caché de datos activos para sesiones, agentes y consultas accedidas con frecuencia
- **Tareas asincrónicas de RocketMQ** — desacoplar ejecuciones de herramientas de larga duración del bucle ReAct mediante procesamiento impulsado por mensajes
- **Recuperación de memoria a largo plazo con Elasticsearch** — habilitar búsqueda híbrida (vectorial + texto completo) para el recuerdo de memoria a largo plazo
- **Gestión de pools de hilos para multi-Agente** — gestión concurrente de tiempo de ejecución de agentes con aislamiento de pools de hilos compartidos y cuotas de recursos

## 📝 Descargo de Responsabilidad

Este es un **proyecto de aprendizaje** creado con fines educativos para explorar la arquitectura y los patrones de implementación de Agentes de IA. Aunque demuestra muchas características similares a las de producción, tenga en cuenta que:

- La base de código aún está en evolución y puede contener errores o implementaciones incompletas
- Algunas características (caché de Redis, tareas asincrónicas de RocketMQ, recuperación con Elasticsearch, pool de hilos multi-agente) están planificadas pero aún no implementadas
- El proyecto prioriza el aprendizaje y la experimentación sobre la preparación para producción
- ¡Las contribuciones, comentarios y sugerencias son bienvenidas!

## 👨‍💻 Desarrollado por

**Ruize Song** — Maestría en Tecnología Informática

Ingeniero de desarrollo backend con experiencia en Java, C++ y Python. Apasionado por la arquitectura de Agentes de IA y los patrones de Ingeniería Harness.

|           |                             |
| --------- | --------------------------- |
| 💻 GitHub | https://github.com/akml2013 |
| 📧 Email  | akmla8@qq.com               |

---

## 📜 Licencia

Este proyecto está licenciado bajo la Licencia MIT.


<p align="center">
  <h1 align="center">🤖 Office Work Agent Harness</h1>
  <p align="center">
    <em>Asistente de Oficina Inteligente Empresarial con Arquitectura de Ingeniería Harness</em>
  </p>
  <p align="center">
    <a href="https://www.java.com/en/download/help/whatis_java.html">
      <img alt="Java 17" src="https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white">
    </a>
    <a href="https://spring.io/projects/spring-boot">
      <img alt="Spring Boot" src="https://img.shields.io/badge/Spring_Boot-3.2-6DB33F?logo=springboot&logoColor=white">
    </a>
    <a href="https://vuejs.org/">
      <img alt="Vue 3" src="https://img.shields.io/badge/Vue-3-4FC08D?logo=vuedotjs&logoColor=white">
    </a>
    <a href="https://element-plus.org/">
      <img alt="Element Plus" src="https://img.shields.io/badge/Element_Plus-2.6-409EFF?logo=elementplus&logoColor=white">
    </a>
    <a href="https://www.docker.com/">
      <img alt="Docker" src="https://img.shields.io/badge/Docker-Ready-2496ED?logo=docker&logoColor=white">
    </a>
  </p>
  <p align="center">
    <a href="https://milvus.io/">
      <img alt="Milvus" src="https://img.shields.io/badge/Milvus-2.4-00A1EA?logo=milvus&logoColor=white">
    </a>
    <a href="https://www.elastic.co/elasticsearch">
      <img alt="Elasticsearch" src="https://img.shields.io/badge/Elasticsearch-8.12-005571?logo=elasticsearch&logoColor=white">
    </a>
    <a href="https://min.io/">
      <img alt="MinIO" src="https://img.shields.io/badge/MinIO-Object_Storage-C72E49?logo=minio&logoColor=white">
    </a>
    <a href="https://rocketmq.apache.org/">
      <img alt="RocketMQ" src="https://img.shields.io/badge/RocketMQ-5.3-D77310?logo=apacherocketmq&logoColor=white">
    </a>
    <a href="https://redis.io/">
      <img alt="Redis" src="https://img.shields.io/badge/Redis-7.2-DC382D?logo=redis&logoColor=white">
    </a>
  </p>
</p>

<p align="center">
  <img src="assets/agent-service.gif" alt="AI Agent Service Demo" width="800">
</p>

## ✨ Características

### 🧠 Motor de Razonamiento ReAct

- **Bucle Pensamiento-Acción-Observación** — el agente razona paso a paso, invoca herramientas y observa los resultados de forma iterativa
- **Una acción por ronda** — cada ronda ReAct invoca una herramienta; las herramientas de documentos agrupan múltiples operaciones mediante la matriz `actions`
- **Gestión de listas de tareas** — descompone automáticamente tareas complejas y sigue el progreso
- **Motor de flujos de trabajo** — define y ejecuta flujos de trabajo automatizados de múltiples pasos con gráfico de nodos DAG
- **Tareas programadas** — crea tareas recurrentes o únicas con reglas de repetición flexibles (ONCE/HOURLY/DAILY/WEEKLY/MONTHLY/YEARLY)
- **Sistema de habilidades** — carga documentos de habilidades específicos del dominio (Word/Excel) para guiar la generación de documentos con formato profesional

### 💾 Sistema de Memoria en Tres Capas

- **Memoria Procedimental** — almacena los patrones de comportamiento y el conocimiento operativo del Agente, incluyendo indicaciones del sistema, documentos de habilidades, definiciones de flujos de trabajo, reglas de ejecución de tareas y conciencia del tiempo actual. Esta capa moldea _cómo_ piensa y actúa el Agente
- **Memoria a Corto Plazo** — mantiene el historial de conversaciones recientes con el mecanismo **AOF (Archivo de Solo Adición) de Reescritura**: los registros de conversación sin procesar (interacciones del usuario, pensamientos de la IA, acciones/resultados de herramientas) se reorganizan por recuperación; fusionando pares de acción-resultado por ronda, eliminando duplicados en operaciones de archivos (manteniendo solo la última escritura por archivo) y truncando a un límite de rondas configurable, garantizando un contexto compacto y relevante sin ruido redundante
- **Memoria a Largo Plazo** — preserva el conocimiento persistente a través de dos estructuras complementarias:
  - **Conversaciones Efectivas** — los últimos N segmentos de diálogo de alta calidad (solicitudes del usuario y respuestas de la IA) que permanecen directamente útiles para el contexto en curso
  - **Resúmenes Históricos** — conversaciones anteriores comprimidas mediante resumen de LLM de modo dual: la _compresión incremental_ agrega nuevos fragmentos de resumen a medida que las conversaciones crecen, mientras que la _compresión completa_ reescribe todo el resumen cuando la cantidad de tokens supera el umbral, asegurando que el resumen permanezca coherente y limitado

### 🔧 Sistema de Herramientas MCP

| Herramienta        | Descripción                                                                                                                                         |
| ------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Word**           | Crear/modificar documentos Word — párrafos, tablas, estilos, encabezados/pies de página, imágenes, marcas de agua, índice y más de 39 operaciones mediante la matriz `actions`         |
| **Excel**          | Crear/modificar hojas de cálculo Excel — celdas, fórmulas, estilos, gráficos, validación de datos, formato condicional y más de 30 operaciones mediante la matriz `actions` |
| **PDF Read**       | Extraer texto y tablas de archivos PDF                                                                                                              |
| **Chart**          | Generar gráficos de visualización de datos (de barras, líneas, circular, dispersión, área)                                                                                  |
| **Knowledge Base** | Cargar y consultar documentos con búsqueda de similitud vectorial (Milvus)                                                                                   |

### 📡 Comunicación en Tiempo Real

- **SSE (Server-Sent Events)** — transmisión en tiempo real de pensamientos de la IA, llamadas a herramientas y resultados al frontend
- **Visualización de flujos de trabajo** — actualizaciones en vivo del estado de los nodos durante la ejecución del flujo de trabajo
- **Progreso de tareas** — actualizaciones en tiempo real de la lista de tareas a medida que el agente trabaja

## 🏗️ Arquitectura

```
┌─────────────┐     SSE      ┌──────────────────┐
│   Vue 3 UI  │◄────────────►│   Spring Boot    │
│  Element+   │   REST/SSE   │   Agent Core     │
└─────────────┘              │                  │
                             │  ┌────────────┐  │
                             │  │ ReAct Loop │  │
                             │  │ Think→Act  │  │
                             │  │ →Observe   │  │
                             │  └─────┬──────┘  │
                             │        │         │
                             │  ┌─────▼──────┐  │
                             │  │ MCP Tools  │  │
                             │  │ System Tool│  │
                             │  └─────┬──────┘  │
                             │        │         │
                             │  ┌─────▼──────┐  │
                             │  │  Memory    │  │
                             │  │ P/S/L Layer│  │
                             │  └────────────┘  │
                             └────────┬─────────┘
                                      │
                    ┌─────────────────┼─────────────────┐
                    │                 │                   │
              ┌─────▼────┐    ┌──────▼─────┐    ┌──────▼─────┐
              │  MySQL   │    │   Milvus   │    │   MinIO    │
              │  Redis   │    │    ES      │    │ RocketMQ   │
              └──────────┘    └────────────┘    └────────────┘
```

## 🚀 Inicio Rápido

### Docker Compose (Recomendado)

```bash
# Clone the repository
git clone https://github.com/your-username/agent-service.git
cd agent-service

# Configure environment variables
cd docker
cp .env.example .env
# Edit .env and fill in your API keys

# Start all services
docker compose up -d

# Access the application
# Frontend: http://localhost:8080
# Backend API: http://localhost:8081
```

<details>
<summary>🐳 Solución de problemas: Fallos al descargar imágenes</summary>

Si encuentra fallos al descargar imágenes debido a problemas de red, utilice el script proporcionado para descargarlas manualmente:

```bash
cd docker
powershell -ExecutionPolicy Bypass -File pull-images.ps1
```

**O descargue las imágenes manualmente:**

```bash
# Configure Docker mirror (Docker Desktop -> Settings -> Docker Engine)
{
  "registry-mirrors": [
    "https://docker.mirrors.ustc.edu.cn",
    "https://hub-mirror.c.163.com",
    "https://mirror.baidubce.com"
  ]
}
```

</details>

### Desarrollo Local

**Requisitos previos:**

- JDK 17+
- Node.js 20+
- MySQL 8.0, Redis 7, Elasticsearch 8, Milvus 2.4, MinIO, RocketMQ 5

**Backend:**

```bash
# Initialize database
mysql -u root -p < sql/agent_service.sql

# Configure application.properties (copy from example)
cp agent-core/src/main/resources/application.properties.example \
   agent-core/src/main/resources/application.properties

# Build and run
mvn clean package -DskipTests
java -jar agent-core/target/agent-core-1.0.0-SNAPSHOT.jar
```

**Frontend:**

```bash
cd agent-ui
npm install
npm run dev
# Access at http://localhost:3000
```

## ⚙️ Configuración

Variables de entorno clave para la implementación con Docker:

| Variable            | Descripción               | Predeterminado                                    |
| ------------------- | ------------------------- | ------------------------------------------ |
| `DEEPSEEK_API_KEY`  | Clave API de DeepSeek LLM      | `your-api-key`                             |
| `DEEPSEEK_BASE_URL` | URL base de la API del LLM          | `https://api.deepseek.com`                 |
| `DEEPSEEK_MODEL`    | Nombre del modelo LLM            | `deepseek-chat`                            |
| `EMBEDDING_API_KEY` | Clave API del servicio de incrustación (embedding) | `your-api-key`                             |
| `EMBEDDING_API_URL` | URL del servicio de incrustación (embedding)     | `https://api.siliconflow.cn/v1/embeddings` |
| `EMBEDDING_MODEL`   | Nombre del modelo de incrustación (embedding)      | `Qwen/Qwen3-Embedding-4B`                  |

Para el desarrollo local, edite `agent-core/src/main/resources/application.properties`.

## 📁 Estructura del Proyecto

```
agent-service/
├── agent-core/            # Spring Boot backend — ReAct engine, MCP tools, memory layer
├── agent-ui/              # Vue 3 frontend — chat UI, workflow editor, file manager
├── agent-common/          # Shared modules (common-core, common-api, common-util)
├── skills/                # AI skill documents (Word/Excel domain knowledge)
├── sql/                   # Database initialization scripts
├── docker/                # Docker Compose & Dockerfiles
│   ├── docker-compose.yml
│   ├── agent-core/
│   │   ├── Dockerfile
│   │   └── application-docker.properties
│   ├── agent-ui/
│   │   ├── Dockerfile
│   │   └── nginx.conf
│   └── rocketmq/
│       └── broker.conf
├── assets/                # Demo images and GIFs
└── docs/                  # Documentation
```

## 🛠️ Stack Tecnológico

**Backend:**

- [Spring Boot 3.2](https://spring.io/projects/spring-boot) — Marco de trabajo de la aplicación
- [MyBatis-Plus](https://baomidou.com/) — Marco de trabajo ORM
- [Apache POI](https://poi.apache.org/) — Procesamiento de documentos Word/Excel
- [Spring AI](https://spring.io/projects/spring-ai) — Marco de integración de IA
- [Milvus](https://milvus.io/) — Base de datos vectorial para búsqueda semántica
- [Elasticsearch](https://www.elastic.co/) — Búsqueda de texto completo y almacenamiento vectorial
- [MinIO](https://min.io/) — Almacenamiento de objetos para gestión de archivos
- [RocketMQ](https://rocketmq.apache.org/) — Cola de mensajes para tareas asincrónicas
- [Redis](https://redis.io/) — Caché y almacenamiento de sesiones

**Frontend:**

- [Vue 3](https://vuejs.org/) — Marco de trabajo JavaScript progresivo
- [Element Plus](https://element-plus.org/) — Biblioteca de componentes de IU
- [Vue Flow](https://vue-flow.dev/) — Visualización de DAG de flujos de trabajo
- [Vite](https://vitejs.dev/) — Herramienta de compilación
- [Pinia](https://pinia.vuejs.org/) — Gestión de estado

## 📍 Hoja de Ruta

### v1.0.0 (Actual)

- Motor de razonamiento ReAct con bucle Pensamiento-Acción-Observación
- Sistema de memoria en tres capas (Procedimental / A Corto Plazo AOF / Resumido a Largo Plazo)
- Herramientas MCP: Word (más de 39 acciones), Excel (más de 30 acciones), Lectura de PDF, Chart, Base de Conocimientos
- Herramientas del sistema: gestión de archivos, CRUD/ejecución de flujos de trabajo, tareas programadas, `ask_user`, consulta de base de conocimientos
- Comunicación SSE en tiempo real con visualización de flujos de trabajo
- Sistema de habilidades para orientación en la generación de documentos específicos del dominio

### v1.1.0 (Planeado)

- **Caché de Redis** — caché de datos activos para sesiones, agentes y consultas accedidas con frecuencia
- **Tareas asincrónicas de RocketMQ** — desacoplar ejecuciones de herramientas de larga duración del bucle ReAct mediante procesamiento impulsado por mensajes
- **Recuperación de memoria a largo plazo con Elasticsearch** — habilitar búsqueda híbrida (vectorial + texto completo) para el recuerdo de memoria a largo plazo
- **Gestión de pools de hilos para multi-Agente** — gestión concurrente de tiempo de ejecución de agentes con aislamiento de pools de hilos compartidos y cuotas de recursos

## 📝 Descargo de Responsabilidad

Este es un **proyecto de aprendizaje** creado con fines educativos para explorar la arquitectura y los patrones de implementación de Agentes de IA. Aunque demuestra muchas características similares a las de producción, tenga en cuenta que:

- La base de código aún está en evolución y puede contener errores o implementaciones incompletas
- Algunas características (caché de Redis, tareas asincrónicas de RocketMQ, recuperación con Elasticsearch, pool de hilos multi-agente) están planificadas pero aún no implementadas
- El proyecto prioriza el aprendizaje y la experimentación sobre la preparación para producción
- ¡Las contribuciones, comentarios y sugerencias son bienvenidas!

## 👨‍💻 Desarrollado por

**Ruize Song** — Maestría en Tecnología Informática

Ingeniero de desarrollo backend con experiencia en Java, C++ y Python. Apasionado por la arquitectura de Agentes de IA y los patrones de Ingeniería Harness.

|           |                             |
| --------- | --------------------------- |
| 💻 GitHub | https://github.com/akml2013 |
| 📧 Email  | akmla8@qq.com               |

---

## 📜 Licencia

Este proyecto está licenciado bajo la Licencia MIT.
