import com.personal.offerguardian.OfferHistoryDeliveryFilter;

public final class OfferHistoryDeliveryFilterStandaloneTest {
    private static void require(boolean ok,String message) {
        if(!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        require(OfferHistoryDeliveryFilter.detectCount("Delivery")==1,"bare Delivery should be single");
        require(OfferHistoryDeliveryFilter.detectCount("Pack & Deliver")==1,"bare Pack & Deliver should be single");
        require(OfferHistoryDeliveryFilter.detectCount("Delivery (2)")==2,"Delivery (2) should be double");
        require(OfferHistoryDeliveryFilter.detectCount("Pack & Deliver (3)")==3,"Pack & Deliver (3) should be triple");
        require(OfferHistoryDeliveryFilter.detectCount("Delivery (multiple)")==0,"ambiguous heading should remain unknown");
        require(OfferHistoryDeliveryFilter.ONE.matchesType("Delivery"),"ONE filter should match bare single");
        require(!OfferHistoryDeliveryFilter.ONE.matchesType("Delivery (2)"),"ONE filter must not match doubles");
        require(OfferHistoryDeliveryFilter.TWO.matchesType("Pack & Deliver (2)"),"TWO filter should match doubles");
        require(!OfferHistoryDeliveryFilter.THREE.matchesType("Delivery"),"THREE filter must not match singles");
        require("type LIKE ?".equals(OfferHistoryDeliveryFilter.TWO.toSql().where),"TWO should use LIKE SQL");
        require(OfferHistoryDeliveryFilter.ONE.toSql().where.contains("type IN ('Delivery','Pack & Deliver')"),"ONE SQL should treat bare headings as singles");
        System.out.println("OfferHistoryDeliveryFilterStandaloneTest passed");
    }
}
