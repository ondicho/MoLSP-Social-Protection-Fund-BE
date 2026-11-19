package MoLSP.go.ke.Social_Assistance_Fund.dtos;

public final class GeographyDtos {
    private GeographyDtos() {
    }

    public record County(int id, String name) {
    }

    public record SubCounty(int subCountyId, String subCountyName, String countyName) {
    }

    public record Ward(String countyName, String subCountyName, String wardName, int wardId) {
    }
}
