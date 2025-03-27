/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package edu.mmdc.motorph;

/**
 *
 * @author Naive
 */

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.stream.Collectors;

import java.util.List;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.DayOfWeek;

import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import java.util.Scanner;

//import lombok.Getter;
//import lombok.Setter;


// ===== Main (Presentation Layer) =====
/*
 * Main class serving as the entry point of the MotorPH application
 * Handles user interaction and delegates tasks to the EmployeeController.
 */
public class MotorPH {
    public static void main(String[] args) {
        System.out.println("Welcome to MotorPH!");

        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        // Delegate the user interactions to Controller(EmployeeController)
        EmployeeController controller = new EmployeeController();

        while (running) {
            System.out.println("\nChoose an option:");
            System.out.println("1. Search by Employee Number");
            System.out.println("2. Display All Employees");
            System.out.println("3. Add Employee");
            System.out.println("4. Exit");
            System.out.print("Enter your choice: ");

            int choice;
            try {
                choice = scanner.nextInt();
                scanner.nextLine(); 
            } catch (Exception e) {
                System.out.println("Invalid input. Please enter a number.");
                scanner.nextLine();
                continue;
            }

            switch (choice) {
                case 1 -> {
                    boolean searchAgain = true;
                    while (searchAgain) {
                        System.out.print("Enter Employee Number to search: ");
                        int employeeNumber = scanner.nextInt();
                        scanner.nextLine();

                        controller.displayEmployeeBySearch(employeeNumber);

                        System.out.print("Do you want to search for another employee? (y/n): ");
                        String response = scanner.nextLine().trim().toLowerCase();

                        if (!response.equals("y")) {
                            searchAgain = false;
                            System.out.println("Returning to the main menu...");
                        }
                    }
                }
                case 2 -> {
                    controller.displayAllEmployees();
                }
                case 3 -> {
                    // Future: Implementation for adding an employee heree
                    System.out.println("Add Employee functionality coming soon.");
                }
                case 4 -> {
                    System.out.println("Exiting program. Goodbye!");
                    running = false;
                }
                default -> System.out.println("Invalid choice. Please select a number from 1 to 4.");
            }
        }

        scanner.close();
    }
}

// ===== Controller Layer =====
/*
 * EmployeeController: Handles user input and delegates tasks to the service layer
 */
class EmployeeController {
    private final EmployeeService employeeService = new EmployeeService();

    public void displayEmployeeBySearch(int employeeNumber) {
        try {
            Employee employee = employeeService.getEmployeeByNumber(employeeNumber);
            if (employee == null) {
                System.out.println("No employee found with Employee Number: " + employeeNumber);
            } else {
                displayEmployeeInfo(employee);
            }
        } catch (Exception e) {
            System.out.println("Error during search: " + e.getMessage());
        }
    }

    public void displayAllEmployees() {
        try {
            List<Employee> employees = employeeService.getAllEmployees();
            if (employees.isEmpty()) {
                System.out.println("No employee data found.");
                return;
            }
            System.out.println("\nAll Employee Details:");
            System.out.println("----------------------------");
            for (Employee employee : employees) {
                displayEmployeeInfo(employee);
            }
            System.out.println("\nTotal number of employees: " + employees.size());
        } catch (Exception e) {
            System.out.println("Error displaying all employees: " + e.getMessage());
        }
    }

    private void displayEmployeeInfo(Employee employee) {
        System.out.println("\n----------------------------");
        System.out.println("----EMPLOYEE INFORMATION----");
        System.out.println("----------------------------");
        System.out.println("Employee Number:    " + employee.getEmployeeNumber());
        System.out.println("Name:               " + employee.getFirstName() + " " + employee.getLastName());
        System.out.println("Birthday:           " + employee.getBirthday());
        System.out.println("Hourly Rate:        " + String.format("%.2f", employee.getHourlyRate()));
        System.out.println("Total Hours Worked: " + String.format("%.2f", employee.getHoursWorked()));
        System.out.println("Weekly Gross Wage:  " + String.format("%.2f", employee.getWeeklyGrossWage()));
        System.out.println("Weekly Net Wage:    " + String.format("%.2f", employee.getWeeklyNetWage()));
        System.out.println("----------------------------");
    }
}

// ===== Service Layer =====
/*
 * EmployeeService: Encapsulates business logic for creating and fetching employee records
 * Integrates with the GoogleSheetsHandler and processors
 */
class EmployeeService {
    
    // Access to data from GoogleSheets to be used locally
    private final GoogleSheetsHandler handler = new GoogleSheetsHandler();
    private final AttendanceProcessor attendanceProcessor = new AttendanceProcessor();

    // Pre-fetched matrices from Google Sheets
    private List<List<Object>> sssMatrix;
    private List<List<Object>> philHealthMatrix;
    private List<List<Object>> pagIbigMatrix;
    private List<List<Object>> withHoldingTaxMatrix;

    // Constructor: Fetch matrices once when the service is created
    public EmployeeService() {
        try {
            this.sssMatrix = GoogleSheetsHandler.fetchSssMatrixData();
            this.philHealthMatrix = GoogleSheetsHandler.fetchPhilHealthMatrixData();
            this.pagIbigMatrix = GoogleSheetsHandler.fetchPagIbigMatrixData();
            this.withHoldingTaxMatrix = GoogleSheetsHandler.fetchWithHoldingTaxMatrixData();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error fetching Google Sheets matrices: " + e.getMessage());
        }
    }
    
    public Employee getEmployeeByNumber(int employeeNumber) throws IOException, GeneralSecurityException {
        List<List<Object>> rawData = handler.fetchEmployeeData();
        Map<Integer, Double> hoursMap = attendanceProcessor.calculateHoursWorked();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        Employee employee = null;

        for (List<Object> row : rawData) {
            if (Integer.parseInt(row.get(0).toString()) == employeeNumber) {
                employee = createEmployee(row, formatter, hoursMap);
                break;
            }
        }
        return employee;
    }

    public List<Employee> getAllEmployees() throws IOException, GeneralSecurityException {
        List<List<Object>> rawData = handler.fetchEmployeeData();
        Map<Integer, Double> hoursMap = attendanceProcessor.calculateHoursWorked();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        List<Employee> employees = new java.util.ArrayList<>();

        for (List<Object> row : rawData) {
            try {
                Employee employee = createEmployee(row, formatter, hoursMap);
                employees.add(employee);
            } catch (Exception e) {
                System.out.println("Error processing employee data: " + row);
                e.printStackTrace();
            }
        }
        return employees;
    }

    // Creates an Employee object from raw data
    private Employee createEmployee(List<Object> row, DateTimeFormatter formatter, Map<Integer, Double> hoursMap) {
        int employeeNumber = Integer.parseInt(row.get(0).toString());
        double hoursWorked = hoursMap.getOrDefault(employeeNumber, 0.0);

        Employee employee = new Employee(
            employeeNumber,
            row.get(1).toString(),
            row.get(2).toString(),
            LocalDate.parse(row.get(3).toString(), formatter),
            row.get(4).toString(),
            row.get(5).toString(),
            row.get(6).toString(),
            row.get(7).toString(),
            row.get(8).toString(),
            row.get(9).toString(),
            row.get(10).toString(),
            row.get(11).toString(),
            row.get(12).toString(),
            Integer.parseInt(row.get(13).toString().replace(",", "")),
            Integer.parseInt(row.get(14).toString().replace(",", "")),
            Integer.parseInt(row.get(15).toString().replace(",", "")),
            Integer.parseInt(row.get(16).toString().replace(",", "")),
            Integer.parseInt(row.get(17).toString().replace(",", "")),
            Double.parseDouble(row.get(18).toString())
        );
        employee.setHoursWorked(hoursWorked);
        
        try {
            // Use the pre-fetched matrices to calculate deductions.
            DeductionService.calculateAllDeductions(employee, sssMatrix, philHealthMatrix, pagIbigMatrix, withHoldingTaxMatrix);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error calculating deductions: " + e.getMessage());
        }

        return employee;
    }
}


// ===== Deduction Layer =====
/*
 * DeductionService: Handles deductions
 */
class DeductionService {
    // Calculate SSS Deduction based on employee data and the SSS matrix.
    public static double calculateSssDeduction(Employee employee, List<List<Object>> sssMatrix) {
        
        double monthlySalary = employee.getBasicSalary();
        
        for (List<Object> row : sssMatrix) {
            String lowerBoundStr = row.get(0).toString().replace(",", "").trim();
            String upperBoundStr = row.get(1).toString().replace(",", "").trim();
            
            double lowerBound = lowerBoundStr.isEmpty() ? 0 : Double.parseDouble(lowerBoundStr);
            double upperBound = upperBoundStr.isEmpty() ? Double.MAX_VALUE : Double.parseDouble(upperBoundStr);
            
            if (lowerBound <= monthlySalary && monthlySalary <= upperBound) {
                double deduction = Double.parseDouble(row.get(2).toString().replace(",", "").trim());
                
                return deduction;
            }
                  
        }
        return 0.0;
    }
    

    
    // Calculate PhilHealth Deduction based on employee data and the PhilHealth matrix.
    public static double calculatePhilHealthDeduction(Employee employee, List<List<Object>> phMatrix) {
        
        double monthlySalary = employee.getBasicSalary();
        
        for (List<Object> row : phMatrix) {
            String lowerBoundStr = row.get(0).toString().replace(",", "").trim();
            String upperBoundStr = row.get(1).toString().replace(",", "").trim();
            
            double lowerBound = lowerBoundStr.isEmpty() ? 0 : Double.parseDouble(lowerBoundStr);
            double upperBound = upperBoundStr.isEmpty() ? Double.MAX_VALUE : Double.parseDouble(upperBoundStr);
            
            if (lowerBound <= monthlySalary && monthlySalary <= upperBound) {
                
                int bracket = Integer.parseInt(row.get(4).toString().trim());
            
                switch (bracket) {
                    case 1 -> {
                        // Fixed monthly premium
                        double deduction = Double.parseDouble(row.get(3).toString().replace(",", "").trim());
                        
                        // Monthly premium contributions are equally shared between the employee and employer.
                        deduction = deduction * 0.5;

                        return deduction;        
                    }
                    case 2 -> {
                        // The 3% premium rate of Philhealth matrix
                        double deduction = monthlySalary * 0.03;

                        // Monthly premium contributions are equally shared between the employee and employer.
                        deduction = deduction * 0.5;
                        return deduction;

                    }
                    case 3 -> {
                        // Fixed monthly premium 
                        double deduction = Double.parseDouble(row.get(3).toString().replace(",", "").trim());
                        
                        // Monthly premium contributions are equally shared between the employee and employer.
                        deduction = deduction * 0.5;

                        return deduction;
                    }
                    default -> {

                        // Handle unexpected bracket values if needed.

                        return 0.0;
                    }
                }
            }
            
           
        }
        return 0.0;
    }
    
    // Calculate Pag-IBIG Deduction based on employee data and the Pag-IBIG matrix.
    public static double calculatePagIbigDeduction(Employee employee, List<List<Object>> pagIbigMatrix) {
        
        double monthlySalary = employee.getBasicSalary();
        
        for (List<Object> row : pagIbigMatrix) {
            String lowerBoundStr = row.get(0).toString().replace(",", "").trim();
            String upperBoundStr = row.get(1).toString().replace(",", "").trim();
            
            double lowerBound = lowerBoundStr.isEmpty() ? 0 : Double.parseDouble(lowerBoundStr);
            double upperBound = upperBoundStr.isEmpty() ? Double.MAX_VALUE : Double.parseDouble(upperBoundStr);
            
            if (lowerBound <= monthlySalary && monthlySalary <= upperBound) {
                double percentageValue = Double.parseDouble(row.get(2).toString().trim());
                percentageValue = percentageValue * 0.01;
                
                double deduction = monthlySalary * percentageValue;
                
                // Max cap for contribution is 100
                if (deduction > 100) {
                    deduction = 100;
                }
                
                return deduction;
            }
        }
       
        return 0.0;
    }
    
    // Adjusted tax deduction calculation to accept taxable wage
    public static double calculateTaxDeduction(Employee employee, List<List<Object>> taxMatrix, double taxableWage) {
        double monthlyTaxable = taxableWage * 4; // Convert to monthly taxable amount

        for (List<Object> row : taxMatrix) {
            String lowerBoundStr = row.get(0).toString().replace(",", "").trim();
            String upperBoundStr = row.get(1).toString().replace(",", "").trim();

            double lowerBound = lowerBoundStr.isEmpty() ? 0 : Double.parseDouble(lowerBoundStr);
            double upperBound = upperBoundStr.isEmpty() ? Double.MAX_VALUE : Double.parseDouble(upperBoundStr);

            if (monthlyTaxable >= lowerBound && monthlyTaxable <= upperBound) {
                double percentageValue = Double.parseDouble(row.get(2).toString().trim());
                double fixedDeduction = Double.parseDouble(row.get(3).toString().replace(",", "").trim());

                double percentage = percentageValue / 100;
                double excess = monthlyTaxable - lowerBound;

                double percentageDeduction = excess * percentage;
                
                double totalMonthlyDeduction = fixedDeduction + percentageDeduction;

                return totalMonthlyDeduction;
            }
        }

        System.out.println("No matching tax bracket found. Returning 0 deduction.");
        return 0.0;
    }

    
    /*
     * Aggregates all deduction calculations.
     * This method can update the Employee object or return an object with a detailed breakdown.
     * For now, it simply prints the calculated values.
     */
    public static void calculateAllDeductions(Employee employee, List<List<Object>> sssMatrix, List<List<Object>> philHealthMatrix, List<List<Object>> pagIbigMatrix, List<List<Object>> withHoldingTaxMatrix) {
    
        // Calculate deductions using respective methods
        double sssDeduction = calculateSssDeduction(employee, sssMatrix);
        double philHealthDeduction = calculatePhilHealthDeduction(employee, philHealthMatrix);
        double pagIbigDeduction = calculatePagIbigDeduction(employee, pagIbigMatrix);
        
//        System.out.println("MONTHLY SSS Deduction: " + sssDeduction);
//        System.out.println("MONTHLY PhilHealth Deduction: " + philHealthDeduction);
//        System.out.println("MONTHLY Pag-IBIG Deduction: " + pagIbigDeduction);
        
        sssDeduction = sssDeduction / 4;
        philHealthDeduction = philHealthDeduction / 4;
        pagIbigDeduction = pagIbigDeduction / 4;
        
//        System.out.println("WEEKLY SSS Deduction: " + sssDeduction);
//        System.out.println("WEEKLY PhilHealth Deduction: " + philHealthDeduction);
//        System.out.println("WEEKLY Pag-IBIG Deduction: " + pagIbigDeduction);
        
        // Calculate taxable wage || Gross - Deductions before proceeding to withHoldingTax
        double taxableWage = employee.getWeeklyGrossWage() - sssDeduction - philHealthDeduction - pagIbigDeduction;

        // Calculate tax deduction using taxable wage instead of gross wage
        double taxDeduction = calculateTaxDeduction(employee, withHoldingTaxMatrix, taxableWage) / 4;
//        System.out.println("Total Weekly Deduction: " + taxDeduction);

        // Calculate net wage
        double netWage = taxableWage - taxDeduction;
        employee.setWeeklyNetWage(netWage);
        
        // Debugging prints
//        System.out.println("TAXABLE WAGE: " + taxableWage);
//        System.out.println("Net Weekly Wage: " + netWage);
    }
}

// ===== Data Access Layer =====
/*
 * GoogleSheetsHandler: Handles interactions with Google Sheets
 */
class GoogleSheetsHandler {
    private static final String APPLICATION_NAME = "MotorPHsample";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    
    private static final String SPREADSHEET_ID = "1bvtvlsjnS-jW8tZ6Sf7pajeM8c1-XZexLrVw3M9dBlE"; // Sheet's ID
    
    // List of variables for the need sheets to play with
    private static final String EMPLOYEE_RANGE = "Employee Details!A2:S35";
    private static final String ATTENDANCE_RANGE = "Attendance Record!A2:F5169";
   
    private static final String SSS_RANGE = "SSS Matrix!A1:C45";
    private static final String PHILHEALTH_RANGE = "Philhealth Matrix!A1:E6";
    private static final String PAGIBIG_RANGE = "Pag-ibig Matrix!A1:E6";
    private static final String WITHHOLDINGTAX_RANGE = "Withholding Tax Matrix!A1:D6";
     
    public static Sheets getSheetsService() throws IOException, GeneralSecurityException {
        GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream("src/main/resources/credentials.json"))
                .createScoped(List.of("https://www.googleapis.com/auth/spreadsheets"));

        return new Sheets.Builder(GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }
    
    ////////////////////////////////////////////////////////////////////////////
    // Boilerplate code that retrives and packs the dataset to be used later ///
    ///////////////////////////////////////////////////////////////////////////
   
    // "Employee Details" sheet
    public static List<List<Object>> fetchEmployeeData() throws IOException, GeneralSecurityException {
        Sheets service = getSheetsService();
        ValueRange response = service.spreadsheets().values().get(SPREADSHEET_ID, EMPLOYEE_RANGE).execute();
        return response.getValues();
    }

    // "Attendance Record" sheet
    public static List<List<Object>> fetchAttendanceData() throws IOException, GeneralSecurityException {
        Sheets service = getSheetsService();
        ValueRange response = service.spreadsheets().values().get(SPREADSHEET_ID, ATTENDANCE_RANGE).execute();
        return response.getValues();
    }
    
    // "SSS Raw Matrix" sheet
    public static List<List<Object>> fetchSssMatrixData() throws IOException, GeneralSecurityException {
        Sheets service = getSheetsService();
        ValueRange response = service.spreadsheets().values().get(SPREADSHEET_ID, SSS_RANGE).execute();
        return response.getValues();
    }
    // "Pag-Ibig Tax Raw Matrix" sheet
    public static List<List<Object>> fetchPagIbigMatrixData() throws IOException, GeneralSecurityException {
        Sheets service = getSheetsService();
        ValueRange response = service.spreadsheets().values().get(SPREADSHEET_ID, PAGIBIG_RANGE).execute();
        return response.getValues();
    }
    // "Philhealth Tax Raw Matrix" sheet
    public static List<List<Object>> fetchPhilHealthMatrixData() throws IOException, GeneralSecurityException {
        Sheets service = getSheetsService();
        ValueRange response = service.spreadsheets().values().get(SPREADSHEET_ID, PHILHEALTH_RANGE).execute();
        return response.getValues();
    }
    // "Withholding Tax Raw Matrix" sheet
    public static List<List<Object>> fetchWithHoldingTaxMatrixData() throws IOException, GeneralSecurityException {
        Sheets service = getSheetsService();
        ValueRange response = service.spreadsheets().values().get(SPREADSHEET_ID, WITHHOLDINGTAX_RANGE).execute();
        return response.getValues();
    }
}

/*
 * AttendanceProcessor: Processes attendance records and computes hours worked.
 */
class AttendanceProcessor {
    public static Map<Integer, Double> calculateHoursWorked() {
        Map<Integer, Double> totalHoursMap = new HashMap<>();
        
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("H:mm"); 
        
        LocalDate latestDate = null;

        try {
            List<List<Object>> data = GoogleSheetsHandler.fetchAttendanceData();
            
            if (data == null || data.isEmpty()) {
                System.out.println("No attendance data found.");
                return totalHoursMap;
            }
   
            // Find the latest date in the data, to be used to find the latest week
            for (List<Object> row : data) {
                try {
                    LocalDate date = LocalDate.parse(row.get(3).toString(), DateTimeFormatter.ofPattern("MM/dd/yyyy"));
                    if (latestDate == null || date.isAfter(latestDate)) {
                        latestDate = date;
                    }
                } catch (Exception e) {
                    System.out.println("Error parsing date for row: " + row);
                    e.printStackTrace();
                }
            }
            
            // Determine the start and end of the latest completed working week
            LocalDate latestWeekStart = latestDate.with(DayOfWeek.MONDAY).minusWeeks(1);
            LocalDate latestWeekEnd = latestDate.with(DayOfWeek.FRIDAY).minusWeeks(1);

            // Calculate hours worked within that week
            for (List<Object> row : data) {
                try {
                    int employeeNumber = Integer.parseInt(row.get(0).toString());
                    LocalDate date = LocalDate.parse(row.get(3).toString(), DateTimeFormatter.ofPattern("MM/dd/yyyy"));
                    String timeInStr = row.get(4).toString();
                    String timeOutStr = row.get(5).toString();

                    if (!date.isBefore(latestWeekStart) && !date.isAfter(latestWeekEnd)) {
                        LocalTime timeIn = LocalTime.parse(timeInStr, timeFormatter);
                        LocalTime timeOut = LocalTime.parse(timeOutStr, timeFormatter);

                        double hoursWorked = Duration.between(timeIn, timeOut).toMinutes() / 60.0;
                        hoursWorked = Math.round(hoursWorked * 100.0) / 100.0;

                        totalHoursMap.put(employeeNumber, totalHoursMap.getOrDefault(employeeNumber, 0.0) + hoursWorked);
                    }

                } catch (Exception e) {
                    System.out.println("Error processing employee data for row: " + row);
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            System.out.println("Error fetching attendance data: " + e.getMessage());
        }

    return totalHoursMap;
}

    // For Debugging Purposes
    public static void printAttendanceProcessor() {
        Map<Integer, Double> totalHoursMap = calculateHoursWorked(); // Directly call the method and get the data

        if (totalHoursMap.isEmpty()) {
            System.out.println("No data available to print.");
            return;
        }

        System.out.println("\nTotal Hours Worked Per Employee:");
        for (Map.Entry<Integer, Double> entry : totalHoursMap.entrySet()) {
            System.out.printf("Employee Number: %d, Total Hours Worked: %.2f%n", entry.getKey(), entry.getValue());
        }
    }
}

// ===== Model Layer =====
/*
 * Employee: Represents an employee with personal and payroll details.
 */
class Employee {
    private int employeeNumber;
    private String lastName;
    private String firstName;
    private LocalDate birthday;
    private String address;
    private String phoneNumber;
    private String sssNumber;
    private String philhealthNumber;
    private String tinNumber;
    private String pagIbigNumber;
    private String status;
    private String position;
    private String immediateSupervisor;
    private int basicSalary;
    private int riceSubsidy;
    private int phoneAllowance;
    private int clothingAllowance;
    private int grossSemiMonthlyRate;
    private double hourlyRate;
    private double hoursWorked;
    private double weeklyGrossWage;
    private double weeklyNetWage;

    public Employee(int employeeNumber, String lastName, String firstName, LocalDate birthday,
                    String address, String phoneNumber, String sssNumber, String philhealthNumber,
                    String tinNumber, String pagIbigNumber, String status, String position,
                    String immediateSupervisor, int basicSalary, int riceSubsidy, int phoneAllowance,
                    int clothingAllowance, int grossSemiMonthlyRate, double hourlyRate) {
        this.employeeNumber = employeeNumber;
        this.lastName = lastName;
        this.firstName = firstName;
        this.birthday = birthday;
        this.address = address;
        this.phoneNumber = phoneNumber;
        this.sssNumber = sssNumber;
        this.philhealthNumber = philhealthNumber;
        this.tinNumber = tinNumber;
        this.pagIbigNumber = pagIbigNumber;
        this.status = status;
        this.position = position;
        this.immediateSupervisor = immediateSupervisor;
        this.basicSalary = basicSalary;
        this.riceSubsidy = riceSubsidy;
        this.phoneAllowance = phoneAllowance;
        this.clothingAllowance = clothingAllowance;
        this.grossSemiMonthlyRate = grossSemiMonthlyRate;
        this.hourlyRate = hourlyRate;
        this.hoursWorked = 0.0;
        this.weeklyGrossWage = 0.0;
        this.weeklyNetWage = 0.0;
    }

    // Getters
    public int getEmployeeNumber() { return employeeNumber; }
    public String getLastName() { return lastName; }
    public String getFirstName() { return firstName; }
    public LocalDate getBirthday() { return birthday; }
    public int getBasicSalary() { return basicSalary; }
    public double getHourlyRate() { return hourlyRate; }
    public double getHoursWorked() { return hoursWorked; }
    public double getWeeklyGrossWage() { return weeklyGrossWage; }
    public double getWeeklyNetWage() { return weeklyNetWage; }

    public void setHoursWorked(double hoursWorked) {
        this.hoursWorked = hoursWorked;
        calculateWeeklyGrossWage();
    }

    private void calculateWeeklyGrossWage() {
        this.weeklyGrossWage = this.hoursWorked * this.hourlyRate;
//        System.out.println("Weekly Gross Wage Calculated: " + this.weeklyGrossWage);
    }

    public void setWeeklyNetWage(double weeklyNetWage) {
        this.weeklyNetWage = weeklyNetWage;
//        System.out.println("Weekly Net Wage Set: " + this.weeklyNetWage);
    }
}