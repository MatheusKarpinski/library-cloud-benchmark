# Imagem da API em container (adaptador Spring Boot). Etapa 6 / ADR-022.
# Construir a partir da RAIZ do projeto:  docker build -t library-api:local .

# ---------- Estágio 1: compilar e empacotar ----------
# JDK 21 + Maven 3.9 (mesma linha de versões do ambiente de desenvolvimento).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY . .
# Só os módulos necessários à API em container (core, api-contract, infrastructure, adapter-container).
# Os testes já foram executados nas Etapas 4 e 5; aqui eles (e suas dependências) são ignorados.
# O cache do Maven acelera as reconstruções (exige BuildKit, padrão no Docker Desktop atual).
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -pl adapter-container -am package -Dmaven.test.skip=true

# ---------- Estágio 2: imagem de execução ----------
# JRE (não JDK) Temurin 21 sobre Ubuntu 24.04 (glibc).
FROM eclipse-temurin:21-jre-noble

# Usuário sem privilégios (a API não precisa de root nem de porta abaixo de 1024).
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app

WORKDIR /app
COPY --from=build --chown=app:app /build/adapter-container/target/library-api.jar /app/library-api.jar

USER app
EXPOSE 8080

# Parâmetro de JVM padrão da imagem: usar até 75% da memória do contêiner como heap.
# É uma VARIÁVEL do experimento (registrar em docs): pode ser trocada com -e JAVA_TOOL_OPTIONS=...
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

# Configuração SOMENTE por variáveis de ambiente (DB_URL, DB_USER, DB_PASSWORD, DB_POOL_*, SERVER_PORT).
# Nenhum segredo é gravado na imagem.
ENTRYPOINT ["java", "-jar", "/app/library-api.jar"]
