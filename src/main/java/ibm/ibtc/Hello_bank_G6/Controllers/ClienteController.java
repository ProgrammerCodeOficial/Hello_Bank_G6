package ibm.ibtc.Hello_bank_G6.Controllers;


import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import ibm.ibtc.Hello_bank_G6.Models.ClienteModel;
import ibm.ibtc.Hello_bank_G6.Models.ContaCorrenteModel;
import ibm.ibtc.Hello_bank_G6.Repositories.IClienteRepository;
import ibm.ibtc.Hello_bank_G6.Repositories.IContaCorrenteRepository;
import ibm.ibtc.Hello_bank_G6.Utils.JWTGenerator;
import ibm.ibtc.Hello_bank_G6.DTO.ClienteCriarDTO;
import ibm.ibtc.Hello_bank_G6.ViewModels.ClienteViewModel;
import ibm.ibtc.Hello_bank_G6.ViewModels.ContaCorrenteViewModel;
import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.print.attribute.standard.JobKOctets;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/cliente")
public class ClienteController {
    private final IClienteRepository _clienteRepository;
    private final IContaCorrenteRepository _contaCorrenteRepository;
    private final PasswordEncoder encoder;

    public ClienteController(IClienteRepository clienteRepository,
                             IContaCorrenteRepository contaCorrenteRepository,
                             PasswordEncoder encoder) {
        this._clienteRepository = clienteRepository;
        this._contaCorrenteRepository = contaCorrenteRepository;
        this.encoder = encoder;
    }

    //Retirar posteriormente
    @GetMapping("/")
    public ResponseEntity<Object> getAll() {
        return ResponseEntity.status(HttpStatus.OK).body(new Object() {
            public final Object clientes = _clienteRepository.findAll();
        });
    }

    @PostMapping("/{param_id}")
    public ResponseEntity<Object> findById(@PathVariable String param_id){
        var cliente = _clienteRepository.findById(UUID.fromString(param_id));

        return (cliente.isEmpty()) ? ResponseEntity.status(HttpStatus.NOT_FOUND).body(new Object() {
            public final Object Mensagem = "Cliente não encontrado.";
        }) : ResponseEntity.status(HttpStatus.OK).body(new Object() {
            public final Object Cliente = cliente.get();
        });

    }

    @PostMapping("/criar")
    public ResponseEntity<Object> createAnimal(@RequestBody ClienteCriarDTO clienteReq) {

        var clienteModel = new ClienteModel();
        var clienteView = new ClienteViewModel();
        var contaCorrenteModel = new ContaCorrenteModel();
        var contaCorrenteView = new ContaCorrenteViewModel();
        var limiteNegativoPadrao = 500.0;

        BeanUtils.copyProperties(clienteReq, clienteModel);
        BeanUtils.copyProperties(clienteReq, clienteView);

        clienteModel.setCreated_at(LocalDateTime.now());
        clienteModel.setUpdated_at(LocalDateTime.now());
        contaCorrenteModel.setCreated_at(LocalDateTime.now());

        contaCorrenteModel.setSaldo(.0);
        contaCorrenteModel.setLimiteNegativo(limiteNegativoPadrao);
        contaCorrenteModel.setClienteModel(clienteModel);

        BeanUtils.copyProperties(contaCorrenteModel, contaCorrenteView);

        clienteModel.setSenha(encoder.encode(clienteModel.getSenha()));

        _clienteRepository.save(clienteModel);
        _contaCorrenteRepository.save(contaCorrenteModel);

        return ResponseEntity.status(HttpStatus.CREATED).body(new Object() {
            public final Object cliente = clienteView;
            public final Object conta = contaCorrenteView;
            public final Object token = new JWTGenerator().gerarJWT(clienteModel.getCpf());
        });
    }

    @DeleteMapping("/deleteById/{param_id}")
    public ResponseEntity<Object> deleteById(@PathVariable String param_id) {

        var cliente = _clienteRepository.findById(UUID.fromString(param_id));

        if (cliente.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new Object() {
                public final Object message = "Cliente não encontrado";
            });
        } else {
            var contaCorrente = _contaCorrenteRepository.findByClienteModel(cliente.get());
            if (contaCorrente.isEmpty()) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new Object() {
                    public final Object message = "Houve um problema com a conta";
                });
            }else {

                if(contaCorrente.get().getSaldo() != 0){
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new Object() {
                        public final Object message = "Ainda a saldo na conta associada a este cliente";
                    });
                }else {
                    _contaCorrenteRepository.deleteById(contaCorrente.get().getId());
                    _clienteRepository.deleteById(UUID.fromString(param_id));
                    return ResponseEntity.status(HttpStatus.OK).body(new Object() {
                        public final Object message = "Cliente deletado";
                    });
                }
            }

        }
    }

    @PutMapping("/updateCliente/{param_id}")
    public ResponseEntity<Object> updateCliente(@PathVariable String param_id, @RequestBody Map<String, Object> req) {

        var cliente = _clienteRepository.findById(UUID.fromString(param_id));

        if (cliente.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new Object() {
                public final Object message = "Cliente não encontrado";
            });
        } else {
            ClienteModel clienteResult = new ClienteModel();
            BeanUtils.copyProperties(cliente.get(), clienteResult);

            if (req.get("nome") != null) clienteResult.setNome((String) req.get("nome"));
            if (req.get("email") != null) clienteResult.setEmail((String) req.get("email"));
            if (req.get("tel") != null) clienteResult.setTel((String) req.get("tel"));
            if (req.get("endereco") != null) clienteResult.setEndereco((String) req.get("endereco"));

            clienteResult.setUpdated_at(LocalDateTime.now());

            _clienteRepository.save(clienteResult);

            return ResponseEntity.status(HttpStatus.OK).body(new Object() {
                public final Object cliente = clienteResult;
            });
        }
    }

    @PostMapping("/login")
    public ResponseEntity<Object> Auth(@RequestBody Map<String, Object> req){
        Optional<ClienteModel> clienteModel;
        if (req.get("cpf") != null && req.get("senha") != null) {

            clienteModel = _clienteRepository.findByCpf(String.valueOf(req.get("cpf")));

            if(clienteModel.isPresent()){

                if (encoder.matches((CharSequence) req.get("senha"), clienteModel.get().getSenha())) {

                    var clienteViewModel = new ClienteViewModel();
                    BeanUtils.copyProperties(clienteModel.get(), clienteViewModel);

                    return ResponseEntity.status(HttpStatus.OK).body(new Object() {
                        public final Object cliente = clienteViewModel;
                        public final Object conta = _contaCorrenteRepository.findByClienteModel(clienteModel.get());
                        public final Object token = new JWTGenerator().gerarJWT(clienteModel.get().getCpf());
                    });
                }else {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new Object() {
                        public final Object mensagem = "Senha incorreta";
                    });
                }
            }else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new Object() {
                    public final Object mensagem = "Usuário não encontrado";
                });
            }
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new Object() {
            public final Object mensagem = "Campo faltante";
        });
    }
}
