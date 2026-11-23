import com.personal.offerguardian.OfferHistoryDeliveryFilter;

public final class OfferHistoryDeliveryFilterStandaloneTest {
    private static void require(boolean ok,String message) {
        if(!ok)throw new AssertionError(message);
    }
    public static void main(String[] args) {
        require(OfferHistoryDeliveryFilter.ALL.matchesCount(0),"ALL includes unknown counts");
        require(OfferHistoryDeliveryFilter.ALL.matchesCount(4),"ALL includes 4+ delivery counts");
        require(OfferHistoryDeliveryFilter.ONE.matchesCount(1),"ONE matches confirmed singles");
        require(OfferHistoryDeliveryFilter.TWO.matchesCount(2),"TWO matches confirmed doubles");
        require(OfferHistoryDeliveryFilter.THREE.matchesCount(3),"THREE matches confirmed triples");
        require(!OfferHistoryDeliveryFilter.ONE.matchesCount(0),"Unknown must not become single");
        require(!OfferHistoryDeliveryFilter.TWO.matchesCount(0),"Unknown must not become double");
        require(!OfferHistoryDeliveryFilter.THREE.matchesCount(2),"Double must not appear under triple");
        for(OfferHistoryDeliveryFilter f:OfferHistoryDeliveryFilter.values()) {
            OfferHistoryDeliveryFilter.Sql sql=f.toSql();
            if(f.active()) {
                require("delivery_count = ?".equals(sql.where),"Filter must use numeric field");
                require(sql.args.length==1&&sql.args[0].equals(Integer.toString(f.count)),
                        "SQL placeholder must reflect selected delivery count");
            } else require("1=1".equals(sql.where),"ALL must include legacy unknowns");
        }
        require(OfferHistoryDeliveryFilter.fromCode("missing")==OfferHistoryDeliveryFilter.ALL,
                "Invalid saved preference falls back to All");
        System.out.println("PASS OfferHistoryDeliveryFilterStandaloneTest");
    }
}
