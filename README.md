# BiomechanicalApp

A web-based 3D biomechanical analysis system that uses LiDAR point cloud data and AI-powered pose estimation to assess human posture, generate clinical reports, and track patient progress over time.

---

## Tech Stack

- **Frontend:** Angular 21, Angular Material, Three.js, Chart.js
- **Backend:** Spring Boot 4.0.2, Java 21, Spring Security, JWT
- **Data Processing:** Python 3.11, Flask, MediaPipe, Open3D, OpenCV
- **Database:** PostgreSQL 18, Flyway
- **Infrastructure:** Docker, Docker Compose, Nginx

---

## Getting Started

### Docker Compose (Recommended)

```bash
git clone <repository-url>
cd BiomechanicalApp
docker-compose up -d
```

| Service | URL |
|---|---|
| Frontend | http://localhost:4200 |
| Backend API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Python service | http://localhost:5000 |

### Local Development

**Frontend**
```bash
cd frontend
npm install
npm start
```

**Backend**
```bash
cd backend
./mvnw spring-boot:run
```

**Data Processing**
```bash
cd data-processing
python -m venv venv311
venv311\Scripts\activate      # Windows
pip install -r requirements.txt
python app.py
```

---

## Features

- Upload `.ply` point cloud files (up to 500 MB) and extract 14+ skeletal keypoints via MediaPipe
- Compute biomechanical metrics: Q-Angle, Forward Head Posture, Shoulder Asymmetry, Global Posture Score
- Interactive Three.js 3D viewer with skeletal overlay color-coded by risk level
- Personalized clinical recommendations with exercise prescriptions
- Scan history with posture trend tracking (improvement / stable / deterioration)
- Role-based dashboards for patients, specialists, researchers, and admins

---

## Environment Variables

Defined in `.env` at the project root:

| Variable | Default |
|---|---|
| `DB_URL` | `jdbc:postgresql://postgres:5432/biomechanics_db` |
| `DB_USERNAME` | `postgres` |
| `DB_PASSWORD` | `postgres` |
| `PYTHON_SERVICE_URL` | `http://data-processing:5000` |
| `JWT_SECRET` | *(set a strong random value in production)* |

---

## User Roles

| Role | Capabilities |
|---|---|
| `PATIENT` | Upload scans, view own results and history |
| `SPECIALIST` | View assigned patients and their scan history |
| `RESEARCHER` | Access aggregate metrics and population trends |
| `ADMIN` | Manage all users, roles, and system statistics |
