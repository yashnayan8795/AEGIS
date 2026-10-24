package io.aegis.inventory.api;
import io.aegis.inventory.application.*;
import io.aegis.inventory.domain.ReservationFailure;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.sql.SQLException;
import org.springframework.core.annotation.Order;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.*;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
@RestControllerAdvice
@Order(1)
public class ReservationErrors extends ResponseEntityExceptionHandler {
 @ExceptionHandler(InvalidReservationRequest.class)
 ProblemDetail invalid(HttpServletRequest request){return problem(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","Request does not match the API contract.",request);}
 @ExceptionHandler(ReservationBusinessException.class)
 ProblemDetail business(ReservationBusinessException e,HttpServletRequest request){
  var status=switch(e.failure()){
   case ITEM_NOT_FOUND,RESERVATION_NOT_FOUND->HttpStatus.NOT_FOUND;
   case MONETARY_OVERFLOW->HttpStatus.BAD_REQUEST;
   default->HttpStatus.CONFLICT;
  };
  return problem(status,e.failure().name(),"Inventory operation has an established business outcome: "+e.failure().name()+".",request);
 }
 @ExceptionHandler({DataAccessResourceFailureException.class,QueryTimeoutException.class,CannotCreateTransactionException.class})
 ProblemDetail unavailable(HttpServletRequest request){return problem(HttpStatus.SERVICE_UNAVAILABLE,"SERVICE_UNAVAILABLE","Inventory storage is unavailable; a mutation outcome may be unknown. Retry the original identity.",request);}
 @ExceptionHandler(TransactionSystemException.class)
 ProblemDetail commitFailure(TransactionSystemException failure,HttpServletRequest request){
  for(Throwable cause=failure;cause!=null;cause=cause.getCause())if(cause instanceof SQLException sql&&sql.getSQLState()!=null&&(sql.getSQLState().startsWith("23")||sql.getSQLState().startsWith("42")))return unexpected(request);
  return unavailable(request);
 }
 @ExceptionHandler(Exception.class)
 ProblemDetail unexpected(HttpServletRequest request){return problem(HttpStatus.INTERNAL_SERVER_ERROR,"INTERNAL_ERROR","An unexpected error occurred.",request);}
 @Override protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException e,HttpHeaders h,HttpStatusCode status,WebRequest request){
  return handleExceptionInternal(e,invalid(((ServletWebRequest)request).getRequest()),h,status,request);
 }
 @Override protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException e,HttpHeaders h,HttpStatusCode status,WebRequest request){
  return handleExceptionInternal(e,problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"UNSUPPORTED_MEDIA_TYPE","Request media type must be application/json.",((ServletWebRequest)request).getRequest()),h,status,request);
 }
 private ProblemDetail problem(HttpStatus status,String code,String detail,HttpServletRequest request){
  var p=ProblemDetail.forStatusAndDetail(status,detail);p.setType(URI.create("about:blank"));p.setTitle(status.getReasonPhrase());p.setInstance(URI.create(request.getRequestURI()));p.setProperty("code",code);return p;
 }
}
