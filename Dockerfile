FROM node:20-alpine

WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY collection.json ./

ENTRYPOINT ["npm", "run", "test"]
