package coo.struts.security.blank.actions.company;

import java.util.List;

import javax.annotation.Resource;

import org.apache.struts2.convention.annotation.Action;

import com.opensymphony.xwork2.ModelDriven;

import coo.base.model.Page;
import coo.core.model.SearchModel;
import coo.core.security.annotations.Auth;
import coo.struts.actions.GenericAction;
import coo.struts.security.blank.entity.Company;
import coo.struts.security.blank.entity.Employee;
import coo.struts.security.blank.service.CompanyService;
import coo.struts.security.blank.service.EmployeeService;
import coo.struts.util.AjaxResultUtils;

/**
 * 职员管理。
 */
@Auth("EMPLOYEE_MANAGE")
public class EmployeeAction extends GenericAction implements
		ModelDriven<SearchModel> {
	@Resource
	private EmployeeService employeeService;
	@Resource
	private CompanyService companyService;
	private SearchModel searchModel = new SearchModel();
	private Page<Employee> employeePage;
	private List<Company> companys;
	private Employee employee;

	@Action("employee-list")
	public String list() {
		employeePage = employeeService.searchEmployee(searchModel);
		return SUCCESS;
	}

	@Action("employee-add")
	public String add() {
		employee = new Employee();
		companys = companyService.getAllCompany();
		return SUCCESS;
	}

	@Action("employee-save")
	public String save() {
		employeeService.createEmployee(employee);
		return AjaxResultUtils.close(
				messageConfig.getString("employee.add.success"),
				"employee-list");
	}

	@Action("employee-edit")
	public String edit() {
		String employeeId = request.getParameter("employeeId");
		employee = employeeService.getEmployee(employeeId);
		companys = companyService.getAllCompany();
		return SUCCESS;
	}

	@Action("employee-update")
	public String update() {
		employeeService.updateEmployee(employee);
		return AjaxResultUtils.close(
				messageConfig.getString("employee.edit.success"),
				"employee-list");
	}

	@Action("employee-delete")
	public String delete() {
		String employeeId = request.getParameter("employeeId");
		employeeService.deleteEmployee(employeeId);
		return AjaxResultUtils.refresh(
				messageConfig.getString("employee.delete.success"),
				"employee-list");
	}

	@Override
	public SearchModel getModel() {
		return searchModel;
	}

	public Page<Employee> getEmployeePage() {
		return employeePage;
	}

	public void setEmployeePage(Page<Employee> employeePage) {
		this.employeePage = employeePage;
	}

	public List<Company> getCompanys() {
		return companys;
	}

	public void setCompanys(List<Company> companys) {
		this.companys = companys;
	}

	public Employee getEmployee() {
		return employee;
	}

	public void setEmployee(Employee employee) {
		this.employee = employee;
	}
}
