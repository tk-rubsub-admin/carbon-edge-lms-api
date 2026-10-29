package com.carbonedge.backend.service;

import com.carbonedge.backend.dto.BulkStudentCreationResponse;
import com.carbonedge.backend.dto.BulkStudentCreationRowResponse;
import com.carbonedge.backend.exception.ForbiddenException;
import com.carbonedge.backend.exception.UnauthorizedException;
import com.carbonedge.backend.model.AuthSession;
import com.carbonedge.backend.model.UserRole;
import com.carbonedge.backend.repository.AuthSessionRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentBulkImportService {

    private static final long MAX_IMPORT_FILE_SIZE = 5L * 1024 * 1024;
    private static final int MAX_IMPORT_ROWS = 1_000;

    private final AuthSessionRepository authSessionRepository;
    private final AuthService authService;

    @Transactional(readOnly = true)
    public BulkStudentCreationResponse bulkCreateStudents(
            String authorizationHeader,
            MultipartFile file
    ) {
        requireAdmin(resolveSession(authorizationHeader));
        List<ExcelStudentRow> studentRows = readStudents(file);
        List<BulkStudentCreationRowResponse> results = new ArrayList<>();
        int createdCount = 0;
        int failedCount = 0;

        for (ExcelStudentRow studentRow : studentRows) {
            String responseUsername = studentRow.username() == null ? "" : studentRow.username();
            try {
                authService.createStudentForManagement(studentRow.username(), studentRow.password());
                createdCount++;
                results.add(new BulkStudentCreationRowResponse(
                        studentRow.rowNumber(),
                        responseUsername,
                        "CREATED",
                        "Student created successfully"
                ));
            } catch (RuntimeException exception) {
                failedCount++;
                log.warn("Bulk student creation row failed: rowNumber={}, exceptionType={}",
                        studentRow.rowNumber(), exception.getClass().getSimpleName());
                String message = trimToNull(exception.getMessage());
                results.add(new BulkStudentCreationRowResponse(
                        studentRow.rowNumber(),
                        responseUsername,
                        "FAILED",
                        message == null ? "Unable to create student" : message
                ));
            }
        }

        return new BulkStudentCreationResponse(
                studentRows.size(),
                createdCount,
                failedCount,
                results
        );
    }

    private List<ExcelStudentRow> readStudents(MultipartFile file) {
        validateFile(file);
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("Excel file does not contain a worksheet");
            }
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            HeaderPosition header = findHeader(sheet, formatter);
            List<ExcelStudentRow> rows = new ArrayList<>();

            for (int rowIndex = header.rowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                String username = trimToNull(formatter.formatCellValue(row.getCell(header.usernameColumn())));
                String rawPassword = formatter.formatCellValue(row.getCell(header.passwordColumn()));
                String password = rawPassword == null || rawPassword.isBlank() ? null : rawPassword;
                if (username == null && password == null) {
                    continue;
                }
                rows.add(new ExcelStudentRow(rowIndex + 1, username, password));
                if (rows.size() > MAX_IMPORT_ROWS) {
                    throw new IllegalArgumentException(
                            "Excel file contains more than " + MAX_IMPORT_ROWS + " students"
                    );
                }
            }

            if (rows.isEmpty()) {
                throw new IllegalArgumentException("Excel file does not contain any students");
            }
            return rows;
        } catch (EncryptedDocumentException exception) {
            throw new IllegalArgumentException("Password-protected Excel files are not supported");
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read Excel file");
        }
    }

    private HeaderPosition findHeader(Sheet sheet, DataFormatter formatter) {
        int lastHeaderCandidate = Math.min(sheet.getLastRowNum(), 19);
        for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= lastHeaderCandidate; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            Integer usernameColumn = null;
            Integer passwordColumn = null;
            for (int columnIndex = row.getFirstCellNum(); columnIndex >= 0 && columnIndex < row.getLastCellNum(); columnIndex++) {
                String value = trimToNull(formatter.formatCellValue(row.getCell(columnIndex)));
                if (value == null) {
                    continue;
                }
                if ("username".equalsIgnoreCase(value)) {
                    usernameColumn = columnIndex;
                } else if ("password".equalsIgnoreCase(value)) {
                    passwordColumn = columnIndex;
                }
            }
            if (usernameColumn != null && passwordColumn != null) {
                return new HeaderPosition(rowIndex, usernameColumn, passwordColumn);
            }
        }
        throw new IllegalArgumentException("Excel file must contain 'username' and 'password' columns");
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Excel file is required");
        }
        if (file.getSize() > MAX_IMPORT_FILE_SIZE) {
            throw new IllegalArgumentException("Excel file must not exceed 5 MB");
        }
        String fileName = file.getOriginalFilename();
        String normalizedFileName = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (!normalizedFileName.endsWith(".xlsx") && !normalizedFileName.endsWith(".xls")) {
            throw new IllegalArgumentException("Only .xlsx and .xls files are supported");
        }
    }

    private AuthSession resolveSession(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Missing bearer token");
        }
        AuthSession session = authSessionRepository.findByAccessToken(authorizationHeader.substring(7).trim())
                .orElseThrow(() -> new UnauthorizedException("Invalid access token"));
        if (!session.isActive()) {
            throw new UnauthorizedException("Session has expired");
        }
        return session;
    }

    private void requireAdmin(AuthSession session) {
        if (session.getUser().getRole() != UserRole.ADMIN) {
            throw new ForbiddenException("Admin access is required");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record ExcelStudentRow(int rowNumber, String username, String password) {
    }

    private record HeaderPosition(int rowIndex, int usernameColumn, int passwordColumn) {
    }
}
