# Port EDI Integration Service

This Spring Boot application implements EDI (Electronic Data Interchange) file processing for port operations, migrating the logic from Oracle stored procedures to a Java-based service.

## Overview

The service processes CODECO (Container Discharge/Loading Order) EDI messages and updates container movement records in the database. It replaces the following Oracle procedures:

- `PROC_PORT_EDI_LOAD_BATCH` - Main batch processing procedure
- `PORT_EDI_READ_TRANSFER` - File reading and transfer logic
- `PORT_EDI_CODECO_UPLOAD` - CODECO file processing
- `PORT_EDI_INSERT_RECORDS` - Database record updates

## Features

- **Automated EDI File Processing**: Monitors target folder for new EDI files
- **CODECO Message Parsing**: Extracts container movement data from EDI messages
- **Database Integration**: Updates container inventory and manifest tables
- **Scheduled Processing**: Configurable cron-based automatic processing
- **Manual Triggers**: REST API endpoints for manual processing
- **File Management**: Moves processed files to success/error folders
- **Error Handling**: Comprehensive logging and error management

## Architecture

### Core Components

1. **EdiProcessingService**: Main service implementing batch processing logic
2. **EdiParserService**: Parses CODECO EDI messages and extracts data
3. **EdiSchedulerService**: Handles scheduled automatic processing
4. **EdiController**: REST endpoints for manual operations

### Database Entities

- **EdiContainerGateInOut**: Tracks container gate movements
- **EdiUploadCodeco**: Stores uploaded EDI file content
- **ShipContainerInventory**: Container inventory records

## Configuration

### Application Properties

```properties
# EDI Processing Configuration
edi.target-folder=./edi-files
edi.success-folder=./edi-files/success
edi.error-folder=./edi-files/error

# Scheduled Processing
scheduler.enabled=true
scheduler.cron=0 */15 * * * ?

# Database Configuration
spring.profiles.active=oracle
```

### Database Profiles

- **Oracle**: Production database configuration
- **PostgreSQL**: Alternative database support
- **H2**: In-memory database for testing

## EDI Message Processing

### Supported Message Types

- **CODECO**: Container Discharge/Loading Order messages
- **Gate In/Out**: Container gate movements (BGM+34/36)
- **Strip/Stuff**: Container stripping/stuffing operations (BGM+999)

### Movement Types Processed

1. **Import Gate Out Full**: Container leaving port after discharge
2. **Empty Gate In**: Empty container returning to depot
3. **Empty Date Out**: Empty container leaving depot
4. **Export Date In Full**: Full container arriving for export
5. **Stripping Import**: Container stripping operations
6. **Stuffing Export**: Container stuffing operations

## API Endpoints

### Manual Processing
```
POST /api/edi/process
```
Triggers manual EDI file processing

### Health Check
```
GET /api/edi/health
```
Returns service health status

## File Processing Flow

1. **File Detection**: Service monitors `edi.target-folder` for new files
2. **Validation**: Checks if file is valid CODECO format
3. **Parsing**: Extracts container movement data from EDI segments
4. **Database Update**: Creates/updates container records
5. **File Movement**: Moves processed files to success/error folders

## EDI Segment Processing

### Key EDI Segments

- **UNB**: Interchange header (file validation)
- **UNH**: Message header (CODECO validation)
- **BGM**: Beginning of message (movement type)
- **EQD**: Equipment details (container number)
- **DTM**: Date/time (movement timestamp)
- **NAD**: Name and address (line code)
- **RFF**: Reference (booking number)

## Database Operations

### Container Movement Updates

The service updates the following tables based on EDI data:

1. **SHIP_BL_MANIFEST_CONTAINER_DTL**: Container manifest details
   - `ISSUE_TO_CONSIGNEE`: Container delivery date
   - `RETURN_FROM_CONSIGNEE`: Container return date

2. **SHIP_MATE_CONTAINER_DTL**: Export container details
   - `ISSUE_TO_SHIPPER`: Container issue to shipper
   - `RETURN_FROM_SHIPPER`: Container return from shipper
   - `EQUIPMENT_SEAL_NO`: Container seal number
   - `GRS_WEIGHT`: Gross weight
   - `VGM_WEIGHT`: Verified gross mass

3. **SHIP_CONTAINER_INVENTORY**: Container inventory movements

## Error Handling

- **File Validation**: Invalid files moved to error folder
- **Duplicate Detection**: Prevents reprocessing of same files
- **Transaction Management**: Database operations wrapped in transactions
- **Logging**: Comprehensive logging for troubleshooting

## Deployment

### Prerequisites

- Java 21+
- Oracle Database (or PostgreSQL for alternative)
- Spring Boot 4.0.0

### Running the Application

```bash
# Using Maven
mvn spring-boot:run

# Using JAR
java -jar portediintegration-0.0.1-SNAPSHOT.jar

# With specific profile
java -jar portediintegration-0.0.1-SNAPSHOT.jar --spring.profiles.active=oracle
```

### Environment Variables

```bash
# Database Configuration
ORACLE_DB_URL=jdbc:oracle:thin:@//host:port/service
ORACLE_DB_USERNAME=username
ORACLE_DB_PASSWORD=password

# EDI Configuration
EDI_TARGET_FOLDER=/path/to/edi/files
EDI_SUCCESS_FOLDER=/path/to/success
EDI_ERROR_FOLDER=/path/to/error

# Scheduler Configuration
SCHEDULER_ENABLED=true
SCHEDULER_CRON="0 */15 * * * ?"
```

## Monitoring

### Logging

The application provides detailed logging for:
- File processing status
- EDI parsing results
- Database operations
- Error conditions

### Health Checks

- Spring Boot Actuator endpoints
- Custom health check endpoint
- Database connectivity monitoring

## Migration Notes

This service replaces the following Oracle procedures:

1. **PROC_PORT_EDI_LOAD_BATCH** → `EdiProcessingService.processEdiFiles()`
2. **PORT_EDI_READ_TRANSFER** → `EdiProcessingService.portEdiReadTransfer()`
3. **PORT_EDI_CODECO_UPLOAD** → `EdiProcessingService.processEdiFile()`
4. **PORT_EDI_INSERT_RECORDS** → `EdiProcessingService.portEdiInsertRecords()`

The Java implementation provides:
- Better error handling and logging
- Configurable processing schedules
- REST API for manual operations
- Improved maintainability and testing

## Testing

Run tests with:
```bash
mvn test
```

The test suite includes:
- Unit tests for EDI parsing
- Integration tests for database operations
- End-to-end processing tests