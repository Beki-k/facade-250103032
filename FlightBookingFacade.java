public class FlightBookingFacade {

    // Encapsulated subsystem references
    private final SeatInventorySystem seatSystem;
    private final BaggagePricingEngine baggageEngine;
    private final CurrencyConverter currencyConverter;
    private final PaymentProcessor paymentProcessor;
    private final TicketIssuer ticketIssuer;

    public FlightBookingFacade() {
        this.seatSystem = new SeatInventorySystem();
        this.baggageEngine = new BaggagePricingEngine();
        this.currencyConverter = new CurrencyConverter();
        this.paymentProcessor = new PaymentProcessor();
        this.ticketIssuer = new TicketIssuer();
    }

    /**
     * Single entry point for booking a complete flight itinerary.
     * Coordinates seat locking, baggage pricing, currency conversion,
     * payment execution, rollback on failure, and ticket issuance.
     */
    public String bookFlight(String passengerName,
                             String flightCode,
                             String seatNumber,
                             double baseFareUSD,
                             int bagCount,
                             double bagWeightKg,
                             String currency,
                             String cardNumber) {
        String lockToken = null;

        try {
            // 1. Connect to the seat server and lock the requested seat.
            seatSystem.connectToReservationServer();
            lockToken = seatSystem.lockSeat(flightCode, seatNumber, passengerName);

            // 2. Calculate baggage fee and add it to the base fare.
            double baggageFeeUSD =
                    baggageEngine.calculateBaggageFeeUSD(bagCount, bagWeightKg);
            double totalUSD = baseFareUSD + baggageFeeUSD;

            // 3. Convert the total into the requested currency.
            double convertedTotal =
                    currencyConverter.convertFromUSD(totalUSD, currency);

            // 4. Initialize the payment gateway and charge the card.
            paymentProcessor.initializeGateway();
            String receiptId =
                    paymentProcessor.chargeCard(cardNumber, convertedTotal, currency);

            // 5. Generate and return the e-ticket.
            return ticketIssuer.generateTicket(
                    passengerName, flightCode, lockToken, receiptId
            );

        } catch (RuntimeException ex) {
            // Release the seat if a later step fails.
            if (lockToken != null) {
                seatSystem.releaseSeatLock(lockToken);
            }
            System.out.println("[FlightBookingFacade] Booking failed: " + ex.getMessage());
            return "BOOKING_FAILED: " + ex.getMessage();

        } finally {
            // Always disconnect from the seat server.
            seatSystem.disconnect();
        }
    }
}
