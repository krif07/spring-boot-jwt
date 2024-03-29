package com.co.cbg.sbt.app.controllers;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.io.IOException;
import java.net.MalformedURLException;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.co.cbg.sbt.app.models.entity.Cliente;
import com.co.cbg.sbt.app.models.service.IClienteService;
import com.co.cbg.sbt.app.models.service.IUploadFileService;
import com.co.cbg.sbt.app.util.paginator.PageRender;
import com.co.cbg.sbt.app.view.xml.ClienteList;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestWrapper;

@Controller
@SessionAttributes("cliente") //Para crear un objecto cliente en la sesion en cada que se guarda
public class ClienteController {

	protected final Log logger = LogFactory.getLog(this.getClass());
	
	@Autowired	
	private IClienteService clienteService;
	
	@Autowired
	private IUploadFileService uploadFileService;
	
	@Autowired
	private MessageSource messageSource;
	
	@Secured({"ROLE_USER"})
	@GetMapping(value="/uploads/{filename:.+}")
	public ResponseEntity<Resource> verFoto(@PathVariable String filename){
				
		Resource recurso = null;
		try {
			recurso = uploadFileService.load(filename);
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\""+ recurso.getFilename() +"\"")
				.body(recurso);
	}
	
	//@Secured("ROLE_USER")
	@PreAuthorize("hasRole('ROLE_USER')") //hasAnyRole('','')
	@GetMapping(value="/ver/{id}")
	public String ver(@PathVariable(value="id") Long id, Map<String, Object> model, RedirectAttributes flash, Locale locale) {
		
		Cliente cliente = clienteService.fetchClienteByIdWithFacturas(id);
		if(cliente==null) {
			flash.addFlashAttribute("error", messageSource.getMessage("text.cliente.flash.db.error", null, locale));
			return "redirect:/listar";
		}
		
		model.put("cliente", cliente);
		model.put("titulo", ": " + cliente.getNombre());
		
		return "ver";
	}
	
	@GetMapping(value= "/api/listar")
	@ResponseBody
	public ClienteList listarRest() {
		
		return new ClienteList(clienteService.findAll());
	}
	
	@RequestMapping(value= {"/listar", "/"}, method=RequestMethod.GET)
	public String listar(@RequestParam(name="page", defaultValue="0") int page, Model model, 
			Authentication authentication,
			HttpServletRequest request,
			Locale locale) {
		
		if(authentication != null) {
			logger.info("Usuario autenticado, tu nombre de usuario es: ".concat(authentication.getName()));
		}
		
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if(auth != null) {
			logger.info("Forma estática: Usuario autenticado " + auth.getName());
		}
		
		if(hasRole("ROLE_ADMIN")) {
			logger.info("Hola ".concat(auth.getName()).concat(", tienes acceso de administrador"));
		}
		else {
			logger.info("Hola ".concat(auth.getName()).concat(", NO tienes acceso"));
		}
		
		SecurityContextHolderAwareRequestWrapper securityContext = new SecurityContextHolderAwareRequestWrapper(request, "ROLE_");
	
		if(securityContext.isUserInRole("ADMIN")) {
			logger.info("Forma SecurityContextHolderAwareRequestWrapper, Hola ".concat(auth.getName()).concat(", tienes acceso de administrador"));
		}
		else {
			logger.info("Forma SecurityContextHolderAwareRequestWrapper, Hola ".concat(auth.getName()).concat(", NO tienes acceso"));
		}
		
		if(request.isUserInRole("ROLE_ADMIN")) {
			logger.info("Forma usando HttpServletRequest, Hola ".concat(auth.getName()).concat(", tienes acceso de administrador"));
		}
		else {
			logger.info("Forma usando HttpServletRequest, Hola ".concat(auth.getName()).concat(", NO tienes acceso"));
		}
		
		Pageable pageRequest = PageRequest.of(page, 5);
		
		Page<Cliente> clientes = clienteService.findAll(pageRequest);
		
		PageRender<Cliente> pageRender = new PageRender<>("/listar", clientes);
		
		model.addAttribute("titulo", messageSource.getMessage("text.cliente.listar.titulo", null, locale));
		model.addAttribute("clientes", clientes);
		model.addAttribute("page",pageRender);
		
		return "listar";
	}
		
	@Secured("ROLE_ADMIN")
	@RequestMapping(value="/form")
	public String crear(Map<String, Object> model, Locale locale) {
		
		Cliente cliente = new Cliente();
		model.put("titulo", messageSource.getMessage("text.cliente.form.titulo.crear", null, locale));
		model.put("cliente", cliente);
		
		return "form";
	}
	
	//@Secured("ROLE_ADMIN")
	@PreAuthorize("hasRole('ROLE_ADMIN')")
	@RequestMapping(value="/form/{id}")
	public String editar(@PathVariable(value="id") Long id, Map<String, Object> model, 
			RedirectAttributes flash, Locale locale) {
	
		Cliente cliente = null;
		
		if(id > 0) {
			cliente = clienteService.findOne(id);
			if(cliente ==  null) {
				flash.addFlashAttribute("error", messageSource.getMessage("text.cliente.flash.db.error", null, locale));
				return "redirect:/listar";
			}
			
		}
		else {
			flash.addFlashAttribute("error", messageSource.getMessage("text.cliente.flash.id.error", null, locale));
			return "redirect:/listar";
		}
		
		model.put("titulo", messageSource.getMessage("text.cliente.form.titulo.editar", null, locale));
		model.put("cliente", cliente);
		
		return "form";
	}
	
	@Secured("ROLE_ADMIN")
	@RequestMapping(value="/form", method=RequestMethod.POST)
	public String guardar(@Valid Cliente cliente, BindingResult result, Model model, 
			@RequestParam("file") MultipartFile foto, RedirectAttributes flash, 
			SessionStatus status, Locale locale) {
		
		if(result.hasErrors()) {
			model.addAttribute("titulo", messageSource.getMessage("text.cliente.form.titulo", null, locale));
			return "form";
		}
		
		
		if(!foto.isEmpty()) {

			if(cliente.getId() != null 
					&& cliente.getId() > 0
					&& cliente.getFoto() != null
					&& cliente.getFoto().length() > 0) {
				
				uploadFileService.delete(cliente.getFoto());
				
			}
			
			String uniqueFileName = null;
			try {
				uniqueFileName = uploadFileService.copy(foto);
			} catch (IOException e) {				
				e.printStackTrace();
			} 
					
			flash.addFlashAttribute("info", messageSource.getMessage("text.cliente.flash.foto.subir.success", null, locale) + "'" + uniqueFileName + "'");
			cliente.setFoto(uniqueFileName);
		}
		
		String messageFlash = (cliente.getId() != null) ? messageSource.getMessage("text.cliente.flash.editar.success", null, locale) : messageSource.getMessage("text.cliente.flash.crear.success", null, locale);
		
		clienteService.save(cliente);		
		status.setComplete(); //Elimina el objeto cliente de la sesion
		
		flash.addFlashAttribute("success", messageFlash);		
		return "redirect:listar";
	}
	
	@Secured("ROLE_ADMIN")
	@RequestMapping(value="/eliminar/{id}")
	public String eliminar(@PathVariable(value="id") Long id, RedirectAttributes flash, Locale locale) {
		
		if(id > 0) {
			Cliente cliente = clienteService.findOne(id);
			
			clienteService.delete(id);
			flash.addFlashAttribute("success",  messageSource.getMessage("text.cliente.flash.eliminar.success", null, locale));
			
			if(uploadFileService.delete(cliente.getFoto())) {				
				String mensajeFotoEliminar = String.format(messageSource.getMessage("text.cliente.flash.foto.eliminar.success", null, locale), cliente.getFoto());
				flash.addFlashAttribute("info", mensajeFotoEliminar);
			}
			
		}
				
		return "redirect:/listar";
	}
	
	private boolean hasRole(String role) {
		
		SecurityContext context = SecurityContextHolder.getContext();
		
		if(context == null) {
			return false;
		}
		
		Authentication auth = context.getAuthentication();
		
		if(auth == null) {
			return false;
		}
		
		Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
		
		return authorities.contains(new SimpleGrantedAuthority(role));
		/*for(GrantedAuthority authority: authorities) {
			if(role.equals(authority.getAuthority())) {
				
				logger.info("Hola usuario ".concat(auth.getName()).concat(", tu role es: ").concat(authority.getAuthority()));
				return true;
			}
		}
		
		return false;*/
	}
}
