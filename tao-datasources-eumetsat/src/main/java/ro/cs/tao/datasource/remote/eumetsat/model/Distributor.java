package ro.cs.tao.datasource.remote.eumetsat.model;

public class Distributor {
    private String individualName;
    private String organisationName;
    private String responsiblePartyRole;
    private String email;
    private String href;

    public String getIndividualName() {
        return individualName;
    }

    public void setIndividualName(String individualName) {
        this.individualName = individualName;
    }

    public String getOrganisationName() {
        return organisationName;
    }

    public void setOrganisationName(String organisationName) {
        this.organisationName = organisationName;
    }

    public String getResponsiblePartyRole() {
        return responsiblePartyRole;
    }

    public void setResponsiblePartyRole(String responsiblePartyRole) {
        this.responsiblePartyRole = responsiblePartyRole;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getHref() {
        return href;
    }

    public void setHref(String href) {
        this.href = href;
    }
}
